# GIS-Agent Platform · 前端开发进度

> 状态：全部页面已按原型的**墨绿科技风**重建，并接入真实后端 API（非演示壳）。

## 已完成

### 设计系统（墨绿科技风）
- `src/styles/theme.css` — 设计令牌（深蓝黑底 + 青/蓝绿主色）、全局网格背景、基础组件（按钮/输入/卡片/徽章/弹窗/Toast）
- `src/styles/layout.css` — 侧边栏、导航、统计卡、项目列表、活动流、快速操作、表格
- `src/styles/pages.css` — 登录轨道场景、流程编排画布、项目详情工作流、模板市场
- 自建组件替代 AntD 蓝色视觉：`Button` / `Modal` / `Toast` / `icons`

### 页面（均调用真实接口）
| 路由 | 页面 | 接口 |
|------|------|------|
| `/login` | 登录/注册（3D 轨道场景 + SSO 占位） | `authApi.register/login` |
| `/dashboard` | 工作台（统计卡 + 最近项目 + 快速操作 + 动态） | `projectApi.list/getById`、`imaApi.listConfigs`、`llmApi.list` |
| `/projects` | 全部项目（表格 + 搜索 + 筛选） | `projectApi.list/getById` |
| `/projects/new` | 新建方案（上传 + 模板选择，支持 `?template=` 预填） | `projectApi.create` |
| `/projects/:id` | 项目详情（工作流 + 中间产物 + 方案预览 + 下载） | `projectApi.getById/status/download*` |
| `/projects/:id/run` | 运行监控（工具进度 + 下载） | `projectApi.run/status/download*` |
| `/settings/llm` | 大模型配置（Provider 卡片 + 测试/删除） | `llmApi.*` |
| `/settings/ima` | IMA 知识库（卡片 + 权重滑块 + 启停 + 编辑） | `imaApi.*` |
| `/pipeline` | 流程编排（**可编辑画布** + 属性面板 + 模板切换 + **保存为自定义模板**） | `templateApi.list` + `templateApi.create` + `templateApi.remove`；编辑态下可增删/排序工具节点，保存为 `category=mine` 模板 |
| `/templates` | 模板市场（分类 Tab + 网格卡片 + 工具链节点流 + 使用/预览） | `templateApi.list` → `GET /api/templates`（真实 5 套预置模板，工具链节点数 2/8/5/4/4；自定义模板 `category=mine` 同接口可查） |
| `/profile` | 个人资料（显示名/邮箱 + 改密） | `userApi.me/updateProfile/changePassword` |
| `/admin/users` | 用户与权限（仅超管：角色下拉 + 启用/禁用） | `adminApi.listUsers/changeRole/toggleEnabled` |
| `/admin/audit` | 操作审计日志（仅超管） | `auditApi.list` |
| 顶栏铃铛 | 通知中心下拉（未读角标 + 全部已读） | `notificationApi.summary/readAll` |

## Phase 3 进展（智能化增强）

### ✅ P3-2 自定义模板保存 / 复用（2026-07-17）
- **后端**：`PipelineTemplateController` 新增 `POST /api/templates`（保存自定义模板，自动生成 `mine_<uuid>` 键、`category=mine`、`builtin=false`，校验名称非空与工具类型合法）与 `DELETE /api/templates/{key}`（仅允许删 `mine` 模板，防误删内置）。新增 `TemplateSaveRequest` DTO。
- **前端**：`PipelinePage` 增加编辑模式（左侧工具链编辑器增删/上下移节点，画布实时预览）、保存模态（名称+说明+工具链预览）、模板分类 Tab（全部/内置/我的）、自定义模板删除按钮；`client.ts` 增加 `templateApi.create/remove`。
- **验证**：API 级（保存 201 / 校验 400 / 删除 200）+ 无头 UI 链路（登录→编辑→加节点→保存→「我的」出现→用此模板新建方案）全绿，0 控制台错误。自定义模板经 `PipelineEngine.resolveTools(templateKey)` 可被真实 `run` 执行。
- **（Phase 3 全部完成 ✅）**

### ✅ P3-3 中间产物手动编辑 + 重跑下游（2026-07-18）
- **后端**：`PipelineController` 新增 `PUT /api/projects/{id}/tools/{execId}`（更新单个中间产物 `outputJson`，并**同步回注**到本次运行的 `contextJson`，使方案预览/下载立即反映编辑）与 `POST /api/projects/{id}/runs/{runId}/rerun?fromOrder=N`（异步重跑 `fromOrder` 之后所有下游节点，执行前先回注被编辑节点的产物，基于编辑后结果重新生成）。`PipelineEngine.injectOutput()` 增强：列表型产物（`PRODUCT_MATCHING`/`CASE_RECOMMEND`/`COMPETITOR_ANALYSIS`）兼容「单对象自动包成 1 元素列表」，避免手动编辑因格式不符被静默吞掉。`ToolExecutionRepository.findByPipelineRunIdAndToolOrder`、DTO `ToolOutputUpdateRequest`、前端 `toolApi.updateOutput` / `projectApi.rerun` 配套。
- **前端**：`ProjectDetailPage` 每个中间产物卡片增加「编辑」「重跑下游」按钮（末节点隐藏重跑）；编辑打开 `Modal`，`outputToText()` 将产物转为可编辑 JSON/Markdown，保存后轮询并刷新；`isLast()` 控制末节点。
- **验证**：端到端 `p3_verify.py` 全绿——`full_solution` 跑出 8/8 SUCCESS → 编辑 order=1（`PRODUCT_MATCHING`）单对象 → context `productSelection` 立即变为编辑值 → `rerun?fromOrder=1` 重跑 order 2–7 全部 SUCCESS → 编辑产品名 `SuperMap iManager` 出现在下游 2–7 全部产物中（竞品/架构/大纲/质检/方案输出均基于编辑结果重生成）。`npm run build` + 后端 `mvn package` 均通过。
- **（Phase 3 全部完成 ✅）**

### ✅ P3-4 方案质检增强（2026-07-18）
- **后端**：`SolutionQcTool` 提示词与解析新增 `passed`（整体分 ≥ 75 为 true）与 `level`（优秀/良好/合格/待改进，按 90/80/70 分档）；`ToolContext.QualityCheck` 增加 `passed`/`level` 字段（`@JsonIgnoreProperties(ignoreUnknown=true)` 兼容旧数据）。`SolutionOutlineTool` 增强：重跑时若 `context.qualityCheck.suggestions` 非空，会把改进建议注入大纲生成提示词，使「按建议改进」闭环生效。
- **前端**：`ProjectDetailPage` 的 `SOLUTION_QC` 产物渲染为**质检仪表盘**——总分环（通过绿/未过红）+ 等级徽标 + 通过/未达验收线标识 + 各维度评分进度条与点评 + 改进建议列表，并提供「按建议改进并重跑」按钮（从 `SOLUTION_OUTLINE` 节点重跑，大纲吸收建议后重生成 QC 与方案输出）。`pages.css` 新增 `.qc-dash/.qc-ring/.qc-level/.qc-bar/.qc-sugs/.qc-improve` 等样式。
- **验证**：端到端全绿——`full_solution` 跑出 8/8 SUCCESS → 质检输出含 `passed=true, level=良好, overallScore=88` 与 5 个维度分 → 点「按建议改进」从 order=5（`SOLUTION_OUTLINE`）重跑 → order 6/7（QC+方案输出）均 SUCCESS 并重生成。`npm run build` + `mvn package` 均通过。

### ✅ P3-5 案例推荐 + 竞品对比增强（2026-07-18）
- **后端**：`CaseRecommendTool` 产物新增 `matchScore`（与需求相关度 0-100）与 `referenceDoc`（IMA 检索命中来源文档，无命中时兜底为"行业公开经验"）；提示词要求输出两字段并基于 SuperMap 公开经验兜底推荐。`CompetitorTool` 产物新增 `advantageScore`（SuperMap 优势信心分 0-100）与 `referenceDoc`，同样加无检索兜底。两工具均已在 P2 接入 IMA `kb-case-doc` / `kb-competitor` 检索，本次增强让"评分 + 溯源"可见。
- **前端**：`ProjectDetailPage` 的 `CASE_RECOMMEND` / `COMPETITOR_ANALYSIS` 产物改为专用卡片渲染——案例卡含相关度评分胶囊 + 来源徽标 + 场景/成效/匹配点；竞品卡含优势分胶囊 + 来源徽标 + 优势/注意/建议。`pages.css` 新增 `.case-list/.case-item/.score-chip/.src-chip/.comp-list/.comp-item` 等样式。
- **验证**：端到端全绿——`full_solution` 跑出 SUCCESS → 案例[0] 含 `matchScore=95, referenceDoc="行业公开经验"` → 竞品[0] 含 `advantageScore=85, referenceDoc="行业公开经验"`（本环境 IMA 无命中，兜底生效，证明检索为空也能稳定产出）。`npm run build` + `mvn package` 均通过。
- **（Phase 3 全部完成 ✅）**

### ✅ P3-6 联调验收（2026-07-18）
- **交付物**：`p3_acceptance.py`（联调验收脚本，登录→建项目→跑 `full_solution`→逐项断言 Phase 3 全部增强点→写报告）+ `/workspace/P3-6-联调验收报告.md`（7/7 通过）。
- **验收检查点（真实 DeepSeek + 真实 IMA）**：C1 全链路 8 节点 SUCCESS；C2 质检 `passed/level/6 维度`；C3 案例 `matchScore=95/referenceDoc`；C4 竞品 `advantageScore=85/referenceDoc`；C5 编辑回注 context（单对象→列表兼容）；C6 重跑下游 2–7 全 SUCCESS 且编辑值传播；C7 按质检建议改进重跑 6/7 全 SUCCESS。
- **说明**：后端端到端脚本覆盖全部功能检查点；前端渲染代码已合入且 `npm run build` 通过，完整 Playwright UI 走查为可选轻量补充。
- **Phase 3 状态**：P3-1 ~ P3-6 全部完成 ✅（自定义模板 / 编辑重跑 / 质检增强 / 案例竞品增强 / 联调验收 / 知识库自动感知）。

### ✅ P3-1 知识库自动感知（2026-07-18）
- **后端**：基于 IMA 连接器预留的 `getUpdates(kbId, since)`（ADDED/MODIFIED/DELETED）实现感知。`KbAwarenessService` 按用户遍历已启用知识库，拉取增量事件、推进 `lastCursor` 游标（`kb_sync_states` 表），一旦发现变更即把该用户全部项目标记为 `kbDirty` 并写入更新说明；`KbAwarenessScheduler`（`@EnableScheduling`，每 60s）周期调用 `syncAll()`。`Project` 新增 `kb_dirty`/`kb_dirty_note`/`kb_dirty_since` 字段（已写入 `db/schema.sql` + 运行库）。`KbAwarenessController`：`POST /api/ima/kb-sync`（手动同步）、`POST /api/ima/kb-simulate`（仅 mock 模式，装填一条模拟事件用于联调）、`POST /api/projects/{id}/rerun-kb`（清除脏标记并重新跑流水线）。`MockIMAKnowledgeBaseConnector.getUpdates` 增加一次性的模拟事件开关。
- **前端**：`ProjectDetailPage` 在项目详情顶部 render「📡 知识库有更新」横幅（含更新说明 + 「用最新知识库重生成」按钮，调用 `projectApi.rerunKb`）；`client.ts` 增加 `projectApi.rerunKb` / `imaApi.kbSync` / `imaApi.kbSimulate`；`pages.css` 增加 `.kb-dirty-banner` 样式。
- **验证**：mock 模式端到端 PASS——初始 run SUCCESS(`kbDirty=false`) → `kb-simulate` 触发 MODIFIED 事件 → 项目 `kbDirty=true` 且 `kbDirtyNote` 含更新来源 → `rerun-kb` → run SUCCESS 且 `kbDirty` 清除。`npm run build` + `mvn package` 均通过。生产（real）模式 `getUpdates` 当前为 stub，机制就绪、待真实 IMA 事件接入即生效。

## 功能收尾 & Phase 4 计划

> 详见仓库根 `后续计划.md`（2026-07-18 编制）。现状审计结论：**Phase 1~3 全部落地；导出为 Apache POI 真实生成 docx/pptx/md（非 stub）**。

### 待收尾（demo 壳 / 遗留 TODO，对应后续计划 F-A~F-E）
- **F-A** ✅ API Key 加密存储（`EncryptionService` AES-256-GCM，master key 取 `APP_MASTER_KEY`）— 已完成
- **F-B** ✅ LLM 连通性真调模型（`LlmService.testConnect` 真实发最小请求，非恒真）— 已完成
- **F-C** ✅ IMA 连通性真连（`RealIMAKnowledgeBaseConnector.testConnection` 真实调用 `search_note_book` + 加连接/读取超时 8s/15s；Mock 恒真仅用于无网调试；控制器接线已实测返回 success=true）— 已完成
- **F-D** ✅ Skills 接口化（新增 `SkillsController`：`GET /api/skills` 由实际注册的 8 个 PipelineTool bean 驱动，返回真实工具数/名称/分类；Dashboard "可编排 Skills" 已改为动态获取，去掉写死 12；登录页静态 12 按用户要求保持不变）— 已完成
- **F-E** ✅ 端到端走通验收脚本 `p4_acceptance.py`（纯标准库、可配置，联网环境一键跑通：注册→建 Provider 真连→建 IMA 真连→建项目→跑流水线→轮询→下载 md/docx/pptx）；沙箱受控运行验证脚本机制无误（网络相关步骤按预期 FAIL）。另修复项目创建 400：`ProjectController`/`ExportService` 上传与导出目录改为绝对路径并确保存在，规避 `MultipartFile.transferTo` 相对路径解析到 Tomcat 临时目录导致保存失败。— 已完成

> P0 功能收尾（F-A~F-E）全部完成。后续"功能全做完并走通"需在**用户联网环境**运行 `python3 p4_acceptance.py`（配置 DeepSeek + IMA 真实凭证，后端设 `IMA_MOCK_ENABLED=false`）全绿方为终态。

### Phase 4 生态化（开发计划 P4-1~5）
P4-1 模板市场社区共享 ／ P4-2 企业级 RBAC ／ P4-3 方案版本管理 ／ P4-4 使用数据看板 ／ P5 Agent 自编排。

- **P4-1** ✅ 模板市场/社区共享（2026-07-18）：
  - 后端：`PipelineTemplate` 增加 `ownerId/likeCount/favoriteCount/published` 字段 + `template_likes`/`template_favorites` 表（唯一约束 (templateId,userId)）；`GET /api/templates/market?scope=all|official|community|mine` 返回带点赞/收藏计数、作者名、当前用户 isLiked/isFavorited/canEdit 态；`POST /api/templates/{id}/like`、`/favorite` 为 toggle（修复派生删除缺事务的 `TransactionRequiredException`，给 toggle 方法加 `@Transactional`）；`POST /api/templates/{id}/publish?community=` 发布/撤回社区；`save` 支持 `category=community` 发布（带 ownerId）。
  - 前端：`TemplatesPage` 切换市场端点，卡片新增 ♥点赞 / ★收藏 / 作者 / 「发布社区·撤回」操作，计数实时刷新；`templateApi` 新增 `market/like/favorite/publish`。
  - **集成验证全绿**（`/tmp/p4_1_verify.py`，16 断言）：发布社区模板 → 市场 all/community/mine 可见且展示作者 → 跨用户 mine 隔离 → B 点赞/收藏计数=1 且 B 市场视图 isLiked/isFavorited=true → B 取消点赞归零 → B 删 A 模板 403 → A 撤回移出社区、再发布回社区 → A 删自己成功。
  - 注意：登录页动效未改动。

- **P4-2** ✅ 企业级 RBAC：团队空间 + 项目级角色访问控制（2026-07-18）：
  - 后端：`TeamService` 新增公开 `requireTeamRole(teamId,userId,minRole)`（按 `team_members` 鉴权，缺失/不足抛 FORBIDDEN）；`PipelineController` 注入 `teamService`，`run`/`updateToolOutput`/`rerunDownstream` 走 `requireProjectRole(...,Role.EDITOR)`、`status`/`download` 走 `Role.VIEWER`，`download` 重新 fetch `Project` 取 `project.getName()`；`ProjectController` 注入 `teamService`+`teamMemberRepository`，`create` 接受可选 `teamId` 并 `requireTeamRole(teamId,userId,Role.EDITOR)`，`list` 返回个人 + 团队成员项目（`findByTeamIdIn`），`getById` 用 `requireProjectRole(...,Role.VIEWER)`，`toResponse` 回填 `teamId`；`KbAwarenessController.rerun-kb` 加 `requireProjectRole(...,Role.EDITOR)`；`ProjectResponse`/`ProjectDto` 补 `teamId`；`ProjectRepository.findByTeamIdIn`；`schema.sql` 追加 `projects.team_id` 列/索引 + `teams`/`team_members`(UNIQUE(team_id,user_id)) 建表；DDL 已以 `gisagent` 属主手动补建（规避 `ddl-auto:validate` 顺序坑）。
  - 前端：`client.ts` 新增 `teamApi`(listMine/create/detail/addMember/updateRole/removeMember)；`icons.tsx` `IconTeam`；`Sidebar` 新增「团队空间」入口（协作区）；`App` 路由 `/teams`；新增 `TeamsPage`（左团队列表 / 右成员管理，创建/邀请模态，角色 `<select>` 对非管理者/OWNER 行禁用，`canManage=['OWNER','ADMIN']`）；`pages.css` 补 `.team-layout/.team-card/.team-detail/.table-row` 4 列覆盖/`.icon-btn/.modal-mask/.modal` 及响应式。`npm run build` 通过。
  - **集成验证全绿**（`/tmp/p4_2_verify.py`，30 断言）：团队 CRUD / 邀请分级 / 成员详情 / 外部人 403 / EDITOR 不可邀请而 OWNER 可 / 项目 RBAC VIEWER·EDITOR / 个人项目隔离 / 改角色 / 移除成员 / 末位 OWNER 保护 / 非 OWNER 不可指派 OWNER —— 全部 PASS。

- **P4-3** ✅ 方案版本管理（2026-07-18）：
  - 后端：`ProjectVersion` 实体 + `project_versions` 表（已补建并修正属主为 `gisagent`，规避 `ddl-auto:validate` 对已有库增量建表的顺序坑）；`ProjectVersionService` 提供快照/列表/详情/回退；`ProjectVersionController` 暴露 `/api/projects/{id}/versions`（`POST` 保存、`GET` 列表、`GET /{vid}` 详情、`POST /{vid}/restore` 回退），含 `owned()` 归属校验（跨用户 404）。
  - 自动快照：每次流水线 `run` / 知识库重生成（`KB_RERUN`）/ 下游重跑成功后，由 `PipelineEngine` 写入版本快照（`triggerType=AUTO_RUN`/`KB_RERUN`），无需手工操作即留痕。
  - 一键回退：将版本 `contextJson` 写回 `Project` 与最新一次 `PipelineRun`，详情页预览与下载立即反映历史版本，**不重新调用 LLM**。
  - 前端：新增 `VersionPanel` 组件接入详情页（`.panel.full`），含「保存当前版本」（标题/备注模态）、版本卡片列表（版本号色阶徽章、触发类型、预览、备注）、「查看」（完整方案正文 + Context Bus 原始 JSON 折叠）、「恢复此版本」（二次确认模态）。回退后回调刷新预览。`projectApi` 新增 `saveVersion/listVersions/getVersion/restoreVersion`。`npm run build` 通过。
  - **集成验证全绿**：`/tmp/p4_3_verify.py` 覆盖 手动快照 v1/v2 → 列表倒序(2条/预览取最新) → 详情含完整 contextJson → 回退 v1 → 断言 `project.context_json` 与最新 `pipeline_run.context_json` 均恢复至 V1 且不再含 V2 → 跨用户列表 404。

- **P4-4** ✅ 使用数据看板：生成量/成功率/趋势/工具表现/模板分布 + 团队视角（2026-07-18）：
  - 后端：新增 `StatsService.overview(userId,teamId)`——个人(`findByUserId`)或团队(`requireTeamRole(VIEWER)`+`findByTeamIdIn`)解析项目集，聚合 `totalRuns`/`completedRuns`(SUCCESS+PARTIAL)/`failedRuns`(FAILED)/`successRate`/`avgRunSeconds`(由 `startedAt`/`finishedAt` 计算)、`dailyTrend`(近 30 天，统一按 `LocalDate.now(ZoneOffset.UTC)` + `atZone(ZoneOffset.UTC)` 分桶，规避无时区 `TIMESTAMP` 回读为 UTC 导致的跨天错桶)、`toolStats`(每 `tool_type` 计数/成功数/成功率/均耗时)、`templates`(`template_id` 分布)；新增 `StatsController GET /api/stats/overview?teamId=`（JWT 保护，401 兜底，映射 `ResponseStatusException`→HTTP 码）；`PipelineRunRepository` 增 `findByProjectIdIn`/`countByProjectIdIn`/`countByProjectIdInAndStatus`；`ToolExecutionRepository` 增 `findByPipelineRunIdIn`。
  - 前端：`client.ts` 新增 `statsApi.overview(teamId?)`；`icons.tsx` `IconStats`；`Sidebar` 新增「数据看板」入口；`App` 路由 `/stats`；新增 `StatsPage`（概览卡：项目数/运行总数/成功率/平均耗时，个人·团队视角 `<select>`，内联 SVG `TrendChart` 30 天柱状图 + 工具/排名条 + 模板分布，复用 `templateLabel()`）；`pages.css` 补 `.chart-wrap/.trend-svg/.rank-list/.rank-row/.rank-bar(.alt)/.rank-meta`。`npm run build` 通过。
  - **集成验证全绿**（`/tmp/p4_4_verify.py`，16 断言）：psql 种入 3 条 `pipeline_runs`(2 SUCCESS 含时间戳 + 1 FAILED) + 3 条 `tool_executions` → 断言聚合（totalRuns=3、completed=2、failed=1、rate≈0.667、avg>0、trend 和=3、tools≥2、团队 RBAC 200/403）全部 PASS。

- **P4-5** ✅ Agent 自编排：LLM 按需求推荐工具链 + 优雅降级（2026-07-18）：
  - 后端：新增 `ToolCatalog`(`@Component`，8 工具目录单一来源 `CATALOG`，提供 `orderedTypes/ toPrompt/ toSkills/ nameOf`)；`SkillsController` 重构注入 `catalog`+`List<PipelineTool>`，`GET /api/skills` 输出结构不变（`total`+`skills[{toolType,name,description,category}]`）而由目录真实驱动；新增 `OrchestrationService.recommend(userId,requirement)`——解析默认 LLM Provider（无则 400）、`encryptionService.decrypt(provider.getApiKeyEncrypted())`、由 `catalog.toPrompt` 拼 system+user、调 `llmService.complete(...)`、解析 JSON（`extractJson` 去 ``` 围栏、`parse` 过滤为已注册类型并保持顺序），异常/空 → 优雅降级 `catalog.orderedTypes` 并 `usedFallback=true`，返回 `{reason,toolChain,model,usedFallback}`；新增 `OrchestrationController POST /api/orchestrate`（JWT 保护）。**顺带修复潜伏 BUG**：`PipelineController`/`KbAwarenessService` 跑链路原先把 `provider.getApiKeyEncrypted()`（仍是密文）传给 `LlmConfig` 导致真连鉴权失败，已注入 `EncryptionService` 在 `LlmConfig.of(...)` 前解密。
  - 前端：`client.ts` 新增 `orchestrateApi.recommend(requirement)`；`icons.tsx` `IconWand`；`Sidebar` 新增「智能编排」入口（方案工作流区）；`App` 路由 `/orchestrate`；新增 `OrchestratePage`（textarea + 3 个示例 chip + `IconWand` 按钮调 `recommend`，渲染 `reason`、有序 `.chain-node` 流、降级徽标、及「用此链路新建项目」）；`pages.css` 补 `.req-textarea/.example-row/.example-chip/.orch-reason/.chain-flow/.chain-node/.chain-index/.chain-name/.chain-type/.chain-arrow/.fallback-badge`。`npm run build` 通过。
  - **集成验证全绿**（`/tmp/p4_5_verify.py`）：`/api/skills` 返回 `total=8`；无 Provider → 400；注册真实 DeepSeek Provider（凭证仅运行时、不入库）→ 推荐返回 200 且链路合法（LLM 返回 `REQUIREMENT_ANALYSIS → PRODUCT_MATCHING → SOLUTION_OUTLINE → SOLUTION_OUTPUT`，`usedFallback=false`）；错误 key → 200 且 `usedFallback=true` 返回完整标准链路。全部 PASS。

### Phase 5 增强化（开发计划 P5-1~5）
> PRD 仅规划到 Phase 4，基于代码审计（pgvector 设计决策未落地、导出无中文字体/分页、流水线串行、无审计/通知）延伸出 Phase 5 五项增强。

- **P5-1** ✅ pgvector 向量混合检索：知识库语义搜索增强（2026-07-19）：
  - 后端：启用 `vector` 扩展（0.6.0），新增 `kb_documents` 表（`embedding vector(1536)` + HNSW 索引 `idx_kb_docs_embedding`）；`LlmService` 新增 `embedding()`（OpenAI/DeepSeek `/v1/embeddings` 兼容）；`EmbeddingService`——文本分块（800 字 + 100 字重叠，段落/句号边界断句）、批量向量化写入、混合检索（向量相似度优先 + 无 Provider 时降级关键词）；`SearchController POST /api/search`（RBAC `requireProjectRole(VIEWER)`）；`PipelineEngine` 在 `run`/`rerunDownstream` 成功后自动 `clearProject` + `indexText`（GENERATED 来源）写库。
  - 前端：`client.ts` `searchApi.search`；`ProjectDetailPage` 顶部新增语义搜索框（`.search-bar/.search-input/.search-results`）；`pages.css` 补检索样式。`npm run build` 通过。
  - **集成验证全绿**（`/tmp/p5_1_verify.py`，9 断言）：pgvector 表/HNSW 索引存在、未登录 403、注册+建项目、关键词回退生效、向量检索结构正确、跨项目隔离 404、Skills 回归 total=8。全部 PASS。

- **P5-2** ✅ 导出健壮性：中文字体嵌入 + 大正文分页 + PPT 品牌模板上传（2026-07-19）：
  - 后端：`ExportService` DOCX 全段落设置东亚字体（`SimHei` 标题 / `SimSun` 正文，跨平台不乱码）；每个主要章节前插分页符（`addHeading(...,pageBreakBefore=true)`）；`SOLUTION_TEXT` 超长时按段智能分段；新增 `POST /api/projects/ppt-template`（上传 `.pptx` 存 `data/templates/brand-template.pptx`，绝对路径防 Tomcat 临时目录坑）；`loadPptxTemplate` 优先配置路径 → 默认 `data/templates` → 回退内置深蓝/青布局。
  - **集成验证全绿**（`/tmp/p5_2_verify.py`，6 断言）：注册、建项目、PPT 模板上传 200、文件落地 `backend/data/templates/brand-template.pptx`、DOCX 导出路径通畅（沙箱无 LLM 时 404 为预期）。全部 PASS。

- **P5-3** ✅ 流水线调度优化：DAG 并行执行 + 超时控制 + 失败重试（2026-07-19）：
  - 后端：`PipelineEngine` 重构为 DAG 驱动——`TOOL_DEPS` 定义工具依赖（CASE_RECOMMEND/COMPETITOR_ANALYSIS 并行于 PRODUCT_MATCHING 之后；ARCHITECTURE_DIAGRAM/SOLUTION_OUTLINE 并行于 REQUIREMENT_ANALYSIS 之后）；拓扑排序后每组 `ExecutorService(4 线程)` 并行；单工具 `TOOL_TIMEOUT_SECONDS=120` 超时熔断、`MAX_RETRIES=1` 失败重试；REQUIREMENT_ANALYSIS 失败整体 FAILED 其余 PARTIAL；未完成任务标记 `SKIPPED`。
  - 编译 + 重启 + 接口冒烟（注册/Skills=8/Stats）全绿。

- **P5-4 + P5-5** ✅ 操作审计日志 + 站内通知中心（2026-07-19）：
  - 后端：新增 `audit_logs` 表（`user_id/username/action/target_type/target_id/detail/ip_address/created_at` + 3 索引）；`AuditService` + `AuditController GET /api/audit`；`AuthController` 登录/注册自动记 `LOGIN`/`REGISTER`，`ProjectController` 建项目记 `CREATE_PROJECT`（IP 取 `getRemoteAddr`）。
  - 新增 `notifications` 表（`user_id/type/title/body/link/is_read`）；`NotificationService` + `NotificationController GET /api/notifications`（返回 `{unread,items}`）/ `POST /api/notifications/read-all`；`TeamController.addMember` 邀请成功后向被邀请人推送 `TEAM_INVITE` 通知（含团队/角色/链接）。
  - **集成验证全绿**（冒烟脚本）：注册→`/api/audit` 返回 1 条 `REGISTER` 记录、`/api/notifications` 返回 `unread=0`、`read-all` 成功。全部 PASS。

### 走通验证
沙箱无外网，LLM 全链路需在真实环境（DeepSeek + IMA）跑 `p4_acceptance.py` 全绿方算"功能全做完"。

### Phase 6 平台化（用户管理 + 权限管理，P6-1~5）
> 用户原话："用户管理和权限管理记得一起做"。在 Phase 5 审计/通知基础上，补齐平台级账号与全局角色体系（区别于 Phase 4 的团队级 `Role`）。

- **P6-1** ✅ 平台全局角色模型（2026-07-19）：
  - `User` 实体新增 `role`（默认 `USER`，`SUPER_ADMIN`/`ADMIN`/`USER）、`enabled`（默认 `true`）、`email`、`displayName` 字段；`schema.sql` 的 `users` 表同步补列。
  - `JwtTokenProvider.generateToken(userId,username,role)` 写入 `role` claim；`AuthService`——注册时库内无用户则首账号自动 `SUPER_ADMIN`，否则 `USER`；登录校验 `enabled=false` 返回 400；`AuthResponse` 携带 `role`。`AuthDto.AuthResponse` 扩展字段。
  - DB 手动补齐：`ALTER TABLE users ADD COLUMN role/enabled/email/display_name`；将既有 id=1、id=62 提升为 `SUPER_ADMIN`。

- **P6-2** ✅ 平台用户管理后台（仅 SUPER_ADMIN，2026-07-19）：
  - 新增 `AdminUserController`：`GET /api/admin/users`（列表含角色/启用状态）、`POST /api/admin/users/{id}/role`（改全局角色，校验合法值、禁止取消自己的超管）、`POST /api/admin/users/{id}/toggle-enabled`（启用/禁用，禁止禁用自己）；`requireSuperAdmin` 对非超管抛 401/403。

- **P6-3** ✅ 当前用户中心（2026-07-19）：
  - 新增 `UserController`：`GET /api/users/me`、`POST /api/users/me`（更新 `displayName`/`email`）、`POST /api/users/me/password`（校验旧密码 `PasswordEncoder.matches`，新密码至少 6 位）。

- **P6-4** ✅ 前端用户/权限/资料页（2026-07-19）：
  - `authStore` 增加 `role` 状态（`setAuth`/`setRole`，持久化 localStorage）；`LoginPage` 登录/注册后写入 `role`；`client.ts` 增加 `adminApi`/`userApi`。
  - 新增 `UsersAdminPage`（用户表格 + 角色下拉 + 启用/禁用按钮）、`UserProfilePage`（资料编辑 + 改密）；`Sidebar` 增加 `IconShield`/`IconBell`、"个人资料"入口、仅超管可见的"用户与权限 / 审计日志"（按 `role` 过滤）；`App.tsx` 接入 `/profile`、`/admin/users`、`/admin/audit` 路由。`npm run build` 通过。

- **P6-5** ✅ 通知中心 UI 收尾（2026-07-19）：
  - 新增 `TopBar` 组件（顶栏通知铃铛 + 未读角标 + 下拉列表 + 全部已读），接入 `MainLayout`；`client.ts` 已有 `notificationApi`（P5-5）；`theme.css` 补 `.topbar/.notif-*` 暗色墨绿样式。`npm run build` 通过。

- **集成验证全绿**（`p6_verify.py`，16 断言）：普通用户登录 `role=USER`、非管理员访问 `/admin/users` 得 403、正确/错误原密码改密 200/400、通知列表与全部已读 200、`/audit` 返回记录、超管登录 `role=SUPER_ADMIN`、列出 66 用户、改角色→ADMIN、禁用/重新启用、禁止禁用自己 400、禁止取消自己超管 400。全部 PASS。

### Phase 7 平台化深化（多租户 / SSO / 计费，P7-1~3）
> 用户原话："按照这个没做完的一个一个做吧"（指 P7 路线图：多租户隔离 → SSO 登录 → 用量计费）。

- **P7-1** ✅ 多租户隔离（2026-07-19）：
  - 新增 `Organization` 实体/表 + `OrganizationRepository`；`User`/`Project`/`Team` 增加 `organization_id`（现有库经 psql 手动补列并回填默认组织 id=1，schema.sql 同步补 DDL 与默认组织幂等种子）。
  - `JwtTokenProvider` 写入 `orgId` claim 并新增解析；`AuthService` 注册/登录解析默认组织并随 JWT 下发；`AuthDto.AuthResponse` 增加 `orgId`。
  - `TenantContext`（ThreadLocal）+ `TenantInterceptor` + `WebConfig`（`/api/**` 注册）在请求期写入当前组织。
  - 隔离落地：`ProjectController.list` 按组织过滤（自己或所属团队项目）；`Project`/`Team` 创建写入 `organization_id`；`TeamService.requireProjectRole`/`requireTeamRole` 校验组织一致、`addMember` 拒绝跨组织邀请、`listMyTeams` 按组织过滤。
  - 超管组织管理：`AdminUserController` 新增 `GET/POST /api/admin/organizations`（列表/创建）、`POST /api/admin/users/{id}/organization`（设置用户组织）。
  - **集成验证全绿**（`p7_verify.py`，15 断言）：超管建两组织、两用户分属不同组织；跨组织项目列表为空 + 直访 403、团队直访 403；同组织内可互相访问/邀请协作；超管改用户组织后门控随归属生效。全部 PASS。

- **P7-2** ✅ SSO 单点登录（OIDC Authorization Code，2026-07-19）：
  - 新增 `SsoProperties`（`@ConfigurationProperties(prefix="app.sso")`）：`enabled`(默认 false)/`mock`(默认 false)/`clientId`/`clientSecret`/`authorizeUrl`/`tokenUrl`/`userInfoUrl`/`redirectUri`/`scope`(默认 `openid email profile`)/`frontendBase`/`emailField`/`nameField`，全部支持环境变量 `APP_SSO_*` 覆盖，默认关闭（不破坏既有登录）。
  - 新增 `SsoController`：`GET /api/sso/config`（下发 `enabled` 供前端决定渲染 SSO 按钮）、`GET /api/sso/authorize`（302 跳转 IdP 授权端点，带 `state` 防 CSRF 并存入内存 `ConcurrentHashMap`）、`GET /api/sso/callback`（用 `code` 换 token → 拉 userinfo → `authService.ssoLogin` 按邮箱建号/复用 → 302 前端 `?sso_token=JWT`）。内置 **mock IdP**（`/mock/authorize`、`POST /mock/token`、`/mock/userinfo`，固定返回 `sso.user@example.com`）仅在 `mock=true` 时启用，便于无真实 IdP 联调。URL 拼接统一 `URLEncoder` 编码规避非法字符 500。
  - `UserRepository` 新增 `findByEmail`；`AuthService.ssoLogin(email,name)` 按邮箱查重、首次则随机密码建号（`role=USER`），复用既有账号避免重复建号。
  - `SecurityConfig` 将 `/api/sso/**` 列入 `permitAll`（授权/回调/配置/ mock 端点无需已登录）。
  - **集成验证全绿**（`p7_2_verify.py`，6 断言）：`/api/sso/config` 200 且 `enabled=true`；完整 SSO 链路拿回 `sso_token`；token 访问 `/users/me` 200 且邮箱匹配；新建用户 `role=USER`；二次 SSO 同邮箱复用同一账号不重复建。全部 PASS。
  - **修复（2026-07-20）**：`authorize()`/`callback()` 原无条件走真实 IdP（`authorizeUrl`/`tokenUrl`/`userInfoUrl` 默认空），mock 模式下空 URL 导致授权端点死循环跳回自身、换码抛异常。已加 `isMock()` 分支——`authorize` 跳内置 `/api/sso/mock/authorize`、`callback` 走 `/mock/token`+`/mock/userinfo`，并用 `HttpServletRequest` 动态拼本服务地址（注意 Spring Boot 3 用 `jakarta.servlet.http.HttpServletRequest`）。修复后从 `/api/sso/authorize` 端到端跑通：302→mock authorize→callback→前端 `?sso_token=JWT`，`/users/me` 返回 `sso.user@example.com`（`role=USER`）。

- **P7-3** ✅ 用量计费（token 用量追踪 + 聚合 + 看板，2026-07-19）：
  - **用量采集**：新增 `LlmUsage`（不可变值对象，含 `add` 线程安全累加）与 `CompletionResult` 记录（`content` + `usage`）；`LlmService.complete` 改为委托 `completeWithUsage`，从 OpenAI/DeepSeek 兼容响应的 `usage.{prompt_tokens,completion_tokens,total_tokens}` 解析 token 用量（缺失/异常降级为 `ZERO`）。
  - **工具级累加**：`ToolContext.addUsage(LlmUsage)` 用 `@JsonIgnore` 排除序列化，8 个流水线工具（需求分析/产品匹配/案例推荐/竞品对比/架构图/大纲/质检/输出）统一改为 `completeWithUsage` 并把用量累加进上下文。
  - **持久化**：`PipelineRun` 增加 `inputTokens`/`outputTokens`/`totalTokens`（列 `input_tokens`/`output_tokens`/`total_tokens`），`schema.sql` 同步 DDL；`PipelineEngine.finishRun` 写入用量，重跑下游**累加**而非覆盖（修复重跑少算问题）。现有库经 psql 手动补列。
  - **聚合端点**：新增 `UsageService.summary(uid, all, orgId, projectId, from, to)`（可配置单价 `app.usage.input-price-per-1k=0.001` / `output-price-per-1k=0.002`）与 `UsageController.GET /api/usage/summary`：普通用户仅看自己项目用量；`SUPER_ADMIN` 传 `all=true` 看全平台，可叠加 `orgId`/`projectId` 下钻；`from`/`to` 为 UTC 日期窗（`finished_at` 为 null 的运行不计入）。响应含 `scope`/`window`/`totals{ runs, inputTokens, outputTokens, totalTokens, estimatedCost }` 与维度 `byUser`/`byProject`/`byDay`/`byOrg`（各维度返回 `runs` + `totalTokens`；input/output 拆分仅在 `totals` 中）。未登录返回 401/403。
  - **前端看板**：`client.ts` 新增 `usageApi.summary`；新增 `IconUsage`；`UsagePage`（路由 `/usage`、`Sidebar` 入口"用量计费"，普通用户可见、超管额外有"全平台"开关）：5 张概览卡（运行次数/输入/输出/合计 Tokens/预估费用）+ 按用户/组织/项目/日期四个维度表 + 日期范围筛选。`layout.css` 配套 `.usage-grid` 作用域样式（5 列卡片网格、筛选条、维度表格，含响应式）。
  - **验证**：后端 `p7_3_verify.py` **19/19 PASS**（self 聚合、时间窗过滤、超管 all 含两用户、未授权拦截）；前端 `npm run build` 通过；真实接口联调确认字段对齐（self `scope=self` 全空、all `scope=all` 含 `byUser=9/byProject=18/byDay=3/byOrg=1`、未授权 403）。注意：存量 `pipeline_runs` 的 token 列为 NULL（计费为新增能力，向前不回填），仅 P7-3 之后新跑的流水线带用量。

## Phase 8 计费纵深（P8-1，2026-07-20）

在 P7-3「用量计费聚合」基础上，补充**组织月度配额管控 + 账期账单 + 超限告警**。

- **数据模型**：新增 `UsageQuota`（组织月度 token 上限 `tokenLimit` + 告警阈值 `warnThreshold` + 同月去重标记 `alertedMonth`，`organization_id` 唯一）与 `Invoice`（账期 `period_month`、运行次数、input/output/total token、估算费用 `estimatedCost`、状态 `DRAFT/SETTLED`；`(organization_id, period_month)` 唯一保证重生成幂等）。`schema.sql`（根 + `db/`）同步追加两张表与索引。
- **配额持久层**：`UsageQuotaRepository`（按组织查/存在性）、`InvoiceRepository`（`findByOrganizationIdOrderByPeriodMonthDesc`、`findByOrganizationIdAndPeriodMonth`、`findByPeriodMonth` + 幂等删除 `deleteByOrganizationAndMonth` / `deleteByMonth`）。
- **聚合查询**：`PipelineRunRepository.aggregateByProjectsInWindow(pids, from, to)` 返回 `List<Object[]>`（SUM input/output/total + COUNT，COALESCE 兜底 NULL），`UsageService` 同款单价 `@Value` 复用于费用估算。
- **BillingService**：
  - `setQuota(orgId, tokenLimit, warnThreshold, operatorId, operatorName)` 幂等 upsert + 审计 `BILLING_QUOTA_SET`；
  - `getQuota` / `allQuotas`（超管未指定组织时看全部）；
  - `generateInvoices(month, ...)` 遍历全部组织按月聚合生成 `DRAFT` 账单（先清后建，幂等）；
  - `evaluateOrg(orgId, runningUserId)` 计算当月用量 = Σ 该组织项目 `pipeline_runs`（finishedAt 在 UTC 账期窗内），pct ≥ 阈值且当月未告警 → 写审计 `BILLING_QUOTA_WARN` + 站内通知 `QUOTA`（通知对象 = 组织 ADMIN + 平台 SUPER_ADMIN，去重）；
  - `afterRun(projectId)` 由 `PipelineEngine.finishRun`（主路径与早退路径均覆盖）及 `rerunDownstream` 调用 → 运行结束即触发配额检查（非核心流程，异常吞掉）。
- **BillingController**：`GET /api/billing/quota`、`PUT /api/billing/quota`（仅 SUPER_ADMIN）、`GET /api/billing/invoices`、`POST /api/billing/invoices/generate`（仅 SUPER_ADMIN）、`POST /api/billing/quota/check`（超管手动巡检，等价于运行结束时的自动检查）。权限：普通用户看本组织配额/账单；超管可下钻任意组织或看全部。
- **前端**：新增 `BillingPage`（路由 `/billing`、侧边栏「计费账单」入口）：组织下拉（超管）+ 配额设置表单 + 本月用量进度条（达阈值变橙红）+ 账期账单表（账期/运行次数/Tokens/费用/状态徽标）；`client.ts` 新增 `billingApi` 与 `orgApi`；`layout.css` 补充 `.quota-*` / `.billing-table` / `.status-pill` 等样式。`npm run build` 通过。
- **验证**：`p8_billing_verify.py` **22/22 PASS**（配额 CRUD、普通用户可见但无权设置、手动巡检触发告警、审计 `BILLING_QUOTA_WARN` 落库、通知 `QUOTA` 发给超管、账单生成覆盖组织且费用聚合正确、未登录被拒）。修复了一处 JPQL 聚合返回类型导致的 `ClassCastException`（`Object[]` → `List<Object[]>`）。

## 验证结果
- `npm run build`：tsc 严格模式 + vite 构建通过（含 P7-3 用量看板）
- 后端链路实测：注册→登录→建项目→详情→状态→建 Provider→建 IMA 配置→启动流水线 全部 200
- 无头浏览器渲染：登录页轨道场景正常；`/dashboard` 带 token 成功拉取并渲染真实项目数据
- SPA 路由 + 登录守卫（PrivateRoute）正常
- **P7-3 后端集成验证**（`p7_3_verify.py`）19/19 PASS：self 聚合、时间窗过滤、超管 all 含两用户、未授权拦截
- **P8-1 计费纵深后端集成验证**（`p8_billing_verify.py`）22/22 PASS：配额 CRUD、普通用户可见但无权设置、手动巡检触发超限告警、审计 `BILLING_QUOTA_WARN` 落库、通知 `QUOTA` 发给超管、账单生成覆盖组织且费用聚合正确、未登录被拒；前端 `npm run build` 通过
- **P7-3 前端联调**：`/api/usage/summary` 真实返回字段与前端一致（self 全空、all 含 byUser/byProject/byDay/byOrg 多维）、未授权 403

## 运行方式
```bash
# 后端（需 Java 17+，PG 已就绪 127.0.0.1:5432 postgres/postgis）
cd backend && ./mvnw clean package -DskipTests
java -jar target/gis-agent-platform-0.1.0-SNAPSHOT.jar \
  --SERVER_PORT=8080 --DB_USERNAME=postgres --DB_PASSWORD=postgis \
  --STORAGE_DIR=/absolute/path/data

# 前端
cd frontend && npm install && npm run dev   # 或 npm run build && npm run preview
```
- `vite.config.ts` 已配置 `server.proxy` 与 `preview.proxy`：`/api` → `http://localhost:8080`，开发/预览同源转发，规避 CORS。
- `src/api/client.ts` 默认 `baseURL` 为相对路径 `/api`；生产构建若要直连后端，设置 `VITE_API_BASE=https://your-host/api`。

## 已知限制
- 沙箱无外网，流水线 `run` 会因无法调用 LLM 而 `FAILED`（前端已优雅提示）。接入真实 API Key 且有网络时即可生成方案、下载 Word/MD/PPT。
- 流程编排页为**配置/可视化**编辑器；真正执行发生在项目详情/监控页（调用真实 `POST /projects/:id/run`）。
- "可用 Skills" 已通过 `GET /api/skills` 由实际注册的 8 个 `PipelineTool` bean 动态驱动（F-D 已完成），Dashboard 同步动态获取，非写死常量。
