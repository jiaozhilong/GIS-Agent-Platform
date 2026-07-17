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
- **未做（后续 P3 项）**：P3-1 知识库自动感知、P3-3 中间产物编辑重跑、P3-4 方案质检增强、P3-5 案例/竞品增强、P3-6 联调验收。

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
