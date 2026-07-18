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

## Phase 3 进展（智能化增强）

### ✅ P3-2 自定义模板保存 / 复用（2026-07-17）
- **后端**：`PipelineTemplateController` 新增 `POST /api/templates`（保存自定义模板，自动生成 `mine_<uuid>` 键、`category=mine`、`builtin=false`，校验名称非空与工具类型合法）与 `DELETE /api/templates/{key}`（仅允许删 `mine` 模板，防误删内置）。新增 `TemplateSaveRequest` DTO。
- **前端**：`PipelinePage` 增加编辑模式（左侧工具链编辑器增删/上下移节点，画布实时预览）、保存模态（名称+说明+工具链预览）、模板分类 Tab（全部/内置/我的）、自定义模板删除按钮；`client.ts` 增加 `templateApi.create/remove`。
- **验证**：API 级（保存 201 / 校验 400 / 删除 200）+ 无头 UI 链路（登录→编辑→加节点→保存→「我的」出现→用此模板新建方案）全绿，0 控制台错误。自定义模板经 `PipelineEngine.resolveTools(templateKey)` 可被真实 `run` 执行。
- **未做（后续 P3 项）**：P3-1 知识库自动感知、P3-6 联调验收。

### ✅ P3-3 中间产物手动编辑 + 重跑下游（2026-07-18）
- **后端**：`PipelineController` 新增 `PUT /api/projects/{id}/tools/{execId}`（更新单个中间产物 `outputJson`，并**同步回注**到本次运行的 `contextJson`，使方案预览/下载立即反映编辑）与 `POST /api/projects/{id}/runs/{runId}/rerun?fromOrder=N`（异步重跑 `fromOrder` 之后所有下游节点，执行前先回注被编辑节点的产物，基于编辑后结果重新生成）。`PipelineEngine.injectOutput()` 增强：列表型产物（`PRODUCT_MATCHING`/`CASE_RECOMMEND`/`COMPETITOR_ANALYSIS`）兼容「单对象自动包成 1 元素列表」，避免手动编辑因格式不符被静默吞掉。`ToolExecutionRepository.findByPipelineRunIdAndToolOrder`、DTO `ToolOutputUpdateRequest`、前端 `toolApi.updateOutput` / `projectApi.rerun` 配套。
- **前端**：`ProjectDetailPage` 每个中间产物卡片增加「编辑」「重跑下游」按钮（末节点隐藏重跑）；编辑打开 `Modal`，`outputToText()` 将产物转为可编辑 JSON/Markdown，保存后轮询并刷新；`isLast()` 控制末节点。
- **验证**：端到端 `p3_verify.py` 全绿——`full_solution` 跑出 8/8 SUCCESS → 编辑 order=1（`PRODUCT_MATCHING`）单对象 → context `productSelection` 立即变为编辑值 → `rerun?fromOrder=1` 重跑 order 2–7 全部 SUCCESS → 编辑产品名 `SuperMap iManager` 出现在下游 2–7 全部产物中（竞品/架构/大纲/质检/方案输出均基于编辑结果重生成）。`npm run build` + 后端 `mvn package` 均通过。
- **未做（后续 P3 项）**：P3-1 知识库自动感知、P3-6 联调验收。

### ✅ P3-4 方案质检增强（2026-07-18）
- **后端**：`SolutionQcTool` 提示词与解析新增 `passed`（整体分 ≥ 75 为 true）与 `level`（优秀/良好/合格/待改进，按 90/80/70 分档）；`ToolContext.QualityCheck` 增加 `passed`/`level` 字段（`@JsonIgnoreProperties(ignoreUnknown=true)` 兼容旧数据）。`SolutionOutlineTool` 增强：重跑时若 `context.qualityCheck.suggestions` 非空，会把改进建议注入大纲生成提示词，使「按建议改进」闭环生效。
- **前端**：`ProjectDetailPage` 的 `SOLUTION_QC` 产物渲染为**质检仪表盘**——总分环（通过绿/未过红）+ 等级徽标 + 通过/未达验收线标识 + 各维度评分进度条与点评 + 改进建议列表，并提供「按建议改进并重跑」按钮（从 `SOLUTION_OUTLINE` 节点重跑，大纲吸收建议后重生成 QC 与方案输出）。`pages.css` 新增 `.qc-dash/.qc-ring/.qc-level/.qc-bar/.qc-sugs/.qc-improve` 等样式。
- **验证**：端到端全绿——`full_solution` 跑出 8/8 SUCCESS → 质检输出含 `passed=true, level=良好, overallScore=88` 与 5 个维度分 → 点「按建议改进」从 order=5（`SOLUTION_OUTLINE`）重跑 → order 6/7（QC+方案输出）均 SUCCESS 并重生成。`npm run build` + `mvn package` 均通过。

### ✅ P3-5 案例推荐 + 竞品对比增强（2026-07-18）
- **后端**：`CaseRecommendTool` 产物新增 `matchScore`（与需求相关度 0-100）与 `referenceDoc`（IMA 检索命中来源文档，无命中时兜底为"行业公开经验"）；提示词要求输出两字段并基于 SuperMap 公开经验兜底推荐。`CompetitorTool` 产物新增 `advantageScore`（SuperMap 优势信心分 0-100）与 `referenceDoc`，同样加无检索兜底。两工具均已在 P2 接入 IMA `kb-case-doc` / `kb-competitor` 检索，本次增强让"评分 + 溯源"可见。
- **前端**：`ProjectDetailPage` 的 `CASE_RECOMMEND` / `COMPETITOR_ANALYSIS` 产物改为专用卡片渲染——案例卡含相关度评分胶囊 + 来源徽标 + 场景/成效/匹配点；竞品卡含优势分胶囊 + 来源徽标 + 优势/注意/建议。`pages.css` 新增 `.case-list/.case-item/.score-chip/.src-chip/.comp-list/.comp-item` 等样式。
- **验证**：端到端全绿——`full_solution` 跑出 SUCCESS → 案例[0] 含 `matchScore=95, referenceDoc="行业公开经验"` → 竞品[0] 含 `advantageScore=85, referenceDoc="行业公开经验"`（本环境 IMA 无命中，兜底生效，证明检索为空也能稳定产出）。`npm run build` + `mvn package` 均通过。
- **未做（后续 P3 项）**：P3-1 知识库自动感知、P3-6 联调验收。

## 验证结果
- `npm run build`：tsc 严格模式 + vite 构建通过
- 后端链路实测：注册→登录→建项目→详情→状态→建 Provider→建 IMA 配置→启动流水线 全部 200
- 无头浏览器渲染：登录页轨道场景正常；`/dashboard` 带 token 成功拉取并渲染真实项目数据
- SPA 路由 + 登录守卫（PrivateRoute）正常

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
- "可用 Skills / 12" 为平台能力常量（暂无独立 skills 接口）。
