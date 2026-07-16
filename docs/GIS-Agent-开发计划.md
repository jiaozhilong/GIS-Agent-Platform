# GIS-Agent 平台 — 项目开发计划

> **基于 PRD v1.2 | 编制日期: 2026-07-16 | 编制: 产品通**
>
> **项目代号**: GIS-Agent
> **代码仓库**: GitHub 新建公共仓库
> **技术栈**: React + TypeScript + Ant Design + React Flow | Java Spring Boot 3.2 + Maven | PostgreSQL 14 + pgvector

---

## 目录

1. [PRD 评审结论](#1-prd-评审结论)
2. [开发前待补充事项](#2-开发前待补充事项)
3. [团队分工建议](#3-团队分工建议)
4. [Phase 1 MVP — 详细任务分解](#4-phase-1-mvp--详细任务分解)
5. [Phase 2-4 — 路线图](#5-phase-2-4--路线图)
6. [里程碑与时间线](#6-里程碑与时间线)
7. [风险与缓解](#7-风险与缓解)

---

## 1. PRD 评审结论

### ✅ 总体判断：可以进入开发

PRD v1.2 质量达标，具备以下条件：
- 问题定义清晰，用户画像具体
- 9 件套工具定义完整，输入/输出/配置项明确
- 系统架构有技术选型和理由
- 分阶段路线图合理（Phase 1 MVP → Phase 4 生态化）
- 风险识别充分

### ⚠️ 评审发现的 6 个待补充点

| # | 问题 | 严重度 | 建议方案 |
|---|------|:---:|------|
| A1 | 用户认证方案未定义 | 🟡 中 | MVP 用 用户名+密码+JWT，后续可扩展 OAuth |
| A2 | IMA MCP 接口契约未确定 | 🟡 中 | 先定义 Java Interface，MVP 用 Mock 实现，真实接口就绪后切换 |
| A3 | Pipeline 错误处理策略未定义 | 🟡 中 | 定义状态机：PENDING→RUNNING→SUCCESS/FAILED/SKIPPED |
| A4 | 文件存储方案未提及 | 🟢 低 | MVP 存本地磁盘+DB 记录路径 |
| A5 | 前后端 API 契约未输出 | 🟡 中 | 开发启动前输出 RESTful API 清单 |
| A6 | 竞品知识库数据来源未定义 | 🟢 低 | Phase 3 再做，不影响 MVP |

---

## 2. 开发前待补充事项

在正式编码前，建议完成以下事项（**预计 2-3 天**）：

| # | 事项 | 负责人 | 产出物 |
|---|------|--------|--------|
| P1 | 输出前后端 API 契约文档（RESTful 接口清单 + 请求/响应 Schema） | 后端 + 前端 | `api-spec.yaml` (OpenAPI 3.0) |
| P2 | 定义 IMA Connector 的 Java Interface（含 Mock 实现） | 后端 | `IMAKnowledgeBaseConnector.java` |
| P3 | 确认 UI 设计稿 / 线框图（至少覆盖：流程画布、IMA配置页、LLM配置页、方案预览页） | UI 设计师 | Figma 设计稿 |
| P4 | 创建 GitHub 仓库 + 配置 CI/CD（Maven 构建 + Vite 构建 + 自动测试） | 开发 | GitHub Repo + Actions |
| P5 | 确定 PPT 模板初版（品牌色、字体、Logo、封面布局） | 方案工程师团队 | `.pptx` 模板文件 |
| P6 | 确认 IMA 知识库测试环境可用（至少 1 个产品文档库 + 1 个案例库） | IMA 对接人 | 测试用知识库 ID |

---

## 3. 团队分工建议

```
┌─────────────┐  ┌─────────────┐  ┌─────────────────┐
│  UI 设计师   │  │  前端开发    │  │   后端开发       │
│  (1人)      │  │  (1-2人)    │  │   (1-2人)       │
├─────────────┤  ├─────────────┤  ├─────────────────┤
│ • 设计稿     │  │ • React SPA │  │ • Spring Boot   │
│ • 交互原型   │  │ • React Flow│  │ • Pipeline 引擎  │
│ • 设计系统   │  │ • Ant Design│  │ • LLM 集成       │
│ • PPT 模板   │  │ • 状态管理  │  │ • IMA 集成       │
│             │  │ • API 对接  │  │ • 文件导出       │
└─────────────┘  └─────────────┘  └─────────────────┘
```

---

## 4. Phase 1 MVP — 详细任务分解

### 🎯 MVP 目标

> 方案工程师登录 → 连接自己的 IMA → 配置自己的大模型 → 上传需求文档 → 20 分钟内拿到产品选型清单 + Word/Markdown 方案文档初稿

### 📋 任务总览

| 模块 | 任务数 | 预估工时 |
|------|:------:|:--------:|
| 基础设施 | 4 | 3 天 |
| 后端核心 | 8 | 12 天 |
| 前端页面 | 6 | 10 天 |
| 集成联调 | 3 | 3 天 |
| **合计** | **21** | **28 天** |

---

### 4.1 🏗️ 基础设施（第 1 周前半）

| ID | 任务 | 描述 | 负责人 | 预估 | 前置 |
|----|------|------|--------|:--:|------|
| **INF-1** | GitHub 仓库初始化 | 创建公共仓库，配置 branch 保护（main/dev），设置 Issue 模板 | 后端 | 0.5d | - |
| **INF-2** | Spring Boot 项目脚手架 | Maven 多模块项目结构（api / core / connector / export），配置 PostgreSQL + pgvector 连接 | 后端 | 1d | INF-1 |
| **INF-3** | React 项目脚手架 | Vite + TypeScript + Ant Design + React Flow，配置路由、API 层封装、环境变量 | 前端 | 1d | INF-1 |
| **INF-4** | 数据库 Schema 设计 | 用户表、项目表、流水线表、工具配置表、LLM Provider 表、IMA 配置表 | 后端 | 0.5d | INF-2 |

#### 数据库核心表

```sql
-- 用户表
users (id, username, password_hash, created_at, updated_at)

-- 项目表（一次方案生成 = 一个项目）
projects (id, user_id, name, status, created_at, updated_at)

-- 流水线执行记录
pipeline_runs (id, project_id, template_id, status, context_json, started_at, finished_at)

-- 工具执行记录
tool_executions (id, pipeline_run_id, tool_type, status, input_json, output_json, error_message, started_at, finished_at)

-- LLM Provider 配置（用户级）
llm_providers (id, user_id, name, type, endpoint, api_key_encrypted, created_at)

-- LLM 模型列表
llm_models (id, provider_id, model_name, context_window, max_tokens)

-- IMA 知识库配置（用户级）
ima_kb_configs (id, user_id, kb_id, kb_name, kb_type, purpose, search_weight, enabled)
```

---

### 4.2 🔧 后端核心（第 1 周后半 ~ 第 2 周）

| ID | 任务 | 描述 | 负责人 | 预估 | 前置 |
|----|------|------|--------|:--:|------|
| **BE-1** | 用户认证模块 | 注册/登录 API，JWT 签发与验证，密码 BCrypt 加密 | 后端 | 1.5d | INF-4 |
| **BE-2** | LLM Provider 管理 API | CRUD 接口：添加/删除/修改 Provider、模型列表管理、连接测试（ping 模型 API） | 后端 | 1.5d | BE-1 |
| **BE-3** | IMA 配置管理 API | 连接/断开知识库、用途标注、权重调节、连接测试 | 后端 | 1.5d | BE-1 |
| **BE-4** | IMA Connector（Mock 实现） | 定义 `IMAKnowledgeBaseConnector` 接口，MVP 阶段提供 Mock 实现（返回预设检索结果） | 后端 | 1d | - |
| **BE-5** | Tool-1 需求分析 | 接收文档（PDF/Word/Text）→ 调用 LLM 提取结构化需求 → 输出 JSON | 后端 | 2d | BE-2, BE-4 |
| **BE-6** | Tool-3 产品匹配 | 接收需求 JSON → 检索 IMA 产品知识库 → LLM 推荐产品 + 匹配理由 | 后端 | 2d | BE-2, BE-4 |
| **BE-7** | Pipeline 引擎（简化版） | 固定顺序执行 Tool-1 → Tool-3，状态机管理，Context Bus 数据传递 | 后端 | 2d | BE-5, BE-6 |
| **BE-8** | 文档导出服务 | 接收 Context Bus 数据 → Apache POI 生成 Word .docx + Markdown 输出 | 后端 | 1.5d | BE-7 |

#### BE-4 IMA Connector 接口定义（关键）

```java
public interface IMAKnowledgeBaseConnector {
    // 验证知识库连接
    boolean testConnection(String kbId, String credential);

    // 检索知识库
    SearchResult search(String kbId, String query, SearchOptions options);

    // 获取知识库元信息
    KBInfo getKBInfo(String kbId);

    // 获取知识库更新事件（用于自动感知）
    List<KBUpdateEvent> getUpdates(String kbId, Instant since);
}
```

#### BE-7 Pipeline 状态机

```
PENDING → RUNNING → SUCCESS
                  → FAILED     (可重试)
                  → PARTIAL    (部分成功，下游继续)

单个 Tool 失败策略（MVP）:
  - Tool-1 失败 → Pipeline FAILED（源头失败，无法继续）
  - Tool-3 失败 → Pipeline PARTIAL（至少拿到了需求分析结果）
```

---

### 4.3 🎨 前端页面（第 2 周 ~ 第 3 周）

| ID | 任务 | 描述 | 负责人 | 预估 | 前置 |
|----|------|------|--------|:--:|------|
| **FE-1** | 登录/注册页面 | 用户名密码登录 + JWT Token 管理（存储/刷新/过期处理） | 前端 | 1d | INF-3, BE-1 |
| **FE-2** | LLM 配置页面 | Provider 列表 + 添加/编辑弹窗 + 模型列表管理 + 连接测试按钮 + 默认模型选择 | 前端 | 1.5d | BE-2 |
| **FE-3** | IMA 配置页面 | 知识库列表 + 连接/断开 + 用途标注下拉 + 权重滑块 + 连接测试 | 前端 | 1.5d | BE-3 |
| **FE-4** | 项目创建页（MVP 简化版） | 上传需求文档 → 选择执行模式（快速选型 / 全套方案）→ 点击"开始生成" | 前端 | 1.5d | BE-7 |
| **FE-5** | 流水线执行页 | 实时展示 Tool 执行状态（进度条 + 日志流），Tool-1/Tool-3 中间结果卡片展示 | 前端 | 2d | BE-7 |
| **FE-6** | 方案预览 + 下载页 | Markdown 在线预览 + Word .docx 下载按钮 | 前端 | 1d | BE-8 |

#### FE-5 流水线执行页 UI 结构

```
┌──────────────────────────────────────────────────┐
│  项目：XX市智慧城市方案                            │
│  状态：🟢 运行中  [Tool 1/2 完成]                 │
├──────────────────────────────────────────────────┤
│  ┌──────────────────┐  ┌──────────────────┐      │
│  │ ✅ 需求分析       │  │ 🔄 产品匹配       │      │
│  │   耗时: 2m30s     │  │   进行中...       │      │
│  │   [展开查看结果]  │  │                  │      │
│  └──────────────────┘  └──────────────────┘      │
├──────────────────────────────────────────────────┤
│  📄 中间产物预览区域                               │
│  ┌────────────────────────────────────────────┐  │
│  │ 需求清单 (Tool-1 输出)                      │  │
│  │ • 功能需求: GIS数据管理、空间分析...        │  │
│  │ • 非功能需求: 并发用户500+、响应时间<2s     │  │
│  │ • 行业场景: 智慧城市                        │  │
│  └────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────┤
│  [下载方案文档 (.docx)]  [下载方案文档 (.md)]     │
└──────────────────────────────────────────────────┘
```

---

### 4.4 🔗 集成联调（第 3 周后半 ~ 第 4 周）

| ID | 任务 | 描述 | 负责人 | 预估 | 前置 |
|----|------|------|--------|:--:|------|
| **INT-1** | 前后端联调 | 全链路测试：登录→配置 LLM/IMA→上传文档→执行流水线→下载方案 | 全员 | 1.5d | 全部 BE+FE |
| **INT-2** | 端到端测试 | 编写 5 条核心 Happy Path 测试用例 + 3 条异常路径 | 后端 | 1d | INT-1 |
| **INT-3** | MVP 验收 + Bug 修复 | 按 PRD 验收标准逐项检查，修复 P0/P1 Bug | 全员 | 1d | INT-2 |

#### MVP 验收标准

| # | 验收条件 | 通过标准 |
|---|---------|---------|
| AC1 | 用户可注册/登录 | 注册成功后可登录并看到主页面 |
| AC2 | 用户可配置自己的 LLM Provider | 添加 OpenAI 兼容 Provider → 连接测试通过 → 选择为默认模型 |
| AC3 | 用户可连接自己的 IMA 知识库 | 输入知识库 ID → 连接测试通过 → 标记用途为 product_doc |
| AC4 | 上传需求文档后执行流水线 | 上传 PDF/Word → 流水线 Tool-1→Tool-3 顺序执行 → 中间结果实时可见 |
| AC5 | 下载完整方案文档 | 方案生成完成后可下载 .docx 和 .md 文件，内容包含需求分析和产品选型 |
| AC6 | 全流程耗时 ≤ 20 分钟 | 从上传文档到下载方案（不含人工等待） |

---

## 5. Phase 2-4 — 路线图

### Phase 2：工具箱完善 + 流程编排（预计 4 周）

```
Week 5-6  补充剩余 7 个 Tool（Tool-2,4,5,6,7,8,9）
Week 7    可视化流程画布（React Flow 拖拽 + 连线 + 节点配置）
Week 8    5 套预置模板 + Context Bus + PPT 导出 + 联调验收
```

| ID | 交付物 | 预估 |
|----|--------|:--:|
| P2-1 | Tool-2 案例推荐 + Tool-4 竞品对比 + Tool-5 架构图生成 | 5d |
| P2-2 | Tool-6 方案框架 + Tool-7 方案质检 + Tool-8 方案输出 + Tool-9 PPT 输出 | 5d |
| P2-3 | 可视化流程画布（React Flow 拖拽节点 + 连线 + 节点属性面板） | 5d |
| P2-4 | 5 套预置模板加载 + 单工具独立 Skill 配置 | 3d |
| P2-5 | Context Bus 完整实现 + PPT 导出（含模板参数配置） | 3d |
| P2-6 | Phase 2 联调 + 验收 | 3d |

### Phase 3：智能化增强（预计 3 周）

| ID | 交付物 | 预估 |
|----|--------|:--:|
| P3-1 | 知识库自动感知更新（IMA MCP 轮询 + 增量索引） | 3d |
| P3-2 | 自定义模板保存/复用 | 2d |
| P3-3 | 中间产物手动编辑 + 重跑下游 | 3d |
| P3-4 | 方案质检工具增强（多维度评分 + 改进建议） | 2d |
| P3-5 | 案例推荐 + 竞品对比工具增强 | 2d |
| P3-6 | Phase 3 联调 + 验收 | 2d |

### Phase 4：生态化（预计 4 周，可并行）

| ID | 交付物 | 预估 |
|----|--------|:--:|
| P4-1 | 模板市场 / 社区共享 | 5d |
| P4-2 | 企业级 RBAC 权限管理 | 5d |
| P4-3 | 方案版本管理（历史版本查看 + 回退） | 3d |
| P4-4 | 使用数据分析看板 | 3d |
| P4-5 | Agent 自编排（LLM 根据需求自动推荐工具链） | 5d |

---

## 6. 里程碑与时间线

```
Week 1        Week 2        Week 3        Week 4        Week 5-8      Week 9-11     Week 12-15
├─────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
│ 🏗️ 基础设施  │ 🔧 后端核心  │ 🎨 前端页面  │ 🔗 联调验收  │ 🧩 Phase 2  │ 🧠 Phase 3  │ 🌐 Phase 4  │
│             │             │             │             │             │             │             │
│ INF-1~4     │ BE-1~8      │ FE-1~6      │ INT-1~3     │ P2-1~6      │ P3-1~6      │ P4-1~5      │
│             │             │             │             │             │             │             │
│ ▲ M1: 仓库  │             │             │ ▲ M2: MVP   │             │ ▲ M3: 全工具 │ ▲ M4: 生态  │
│    就绪     │             │             │    交付     │             │    上线     │    上线    │
└─────────────┴─────────────┴─────────────┴─────────────┴─────────────┴─────────────┴─────────────┘

M1 (Week 1):    项目脚手架就绪 + DB Schema + API 契约
M2 (Week 4):    MVP 验收通过，可演示端到端流程
M3 (Week 11):   9 件套全工具 + 流程画布 + PPT 导出上线
M4 (Week 15):   模板市场 + RBAC + 版本管理上线
```

### 工时汇总

| 阶段 | 内容 | 预估工时 |
|------|------|:--:|
| 开发前准备 | API 契约 + 设计稿 + Mock 接口 | 3d |
| Phase 1 MVP | 基础设施 + 后端 + 前端 + 联调 | 28d |
| Phase 2 | 工具箱完善 + 流程编排 | 20d |
| Phase 3 | 智能化增强 | 14d |
| Phase 4 | 生态化 | 20d |
| **合计** | | **≈85 人天** |

> 注：以上为单人串行估算。实际可由 2-3 人并行开发，Phase 1 压缩到 **4 周**。

---

## 7. 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|:--:|------|
| IMA MCP 接口延迟/不可用 | 阻塞 BE-4/BE-6 开发 | 中 | **已设计 Mock 接口**，前后端可独立开发，真实接口就绪后切换 |
| LLM API 成本失控 | 开发测试阶段账单高 | 低 | 开发阶段统一用 DeepSeek-V3（低成本），GPT-4o 仅验收测试用 |
| 前后端 API 理解不一致 | 联调返工 | 中 | **开发前输出 OpenAPI Spec**，前端可据此生成 Mock Server |
| PPT 模板参数不明确 | FE-6/P2-5 返工 | 低 | 开发前从方案工程师团队获取品牌规范文档 |
| pgvector 向量检索性能不达标 | 产品匹配准确率低 | 低 | Phase 1 以关键词检索为主，pgvector 做增强，Phase 3 再优化 |

---

## 8. 附录：Phase 1 前后端 API 清单（概要）

> 详细 OpenAPI Spec 在开发前补充

| 方法 | 路径 | 说明 | Phase |
|------|------|------|:----:|
| POST | `/api/auth/register` | 用户注册 | P1 |
| POST | `/api/auth/login` | 用户登录，返回 JWT | P1 |
| GET | `/api/providers` | 获取用户 LLM Provider 列表 | P1 |
| POST | `/api/providers` | 添加 LLM Provider | P1 |
| DELETE | `/api/providers/{id}` | 删除 Provider | P1 |
| POST | `/api/providers/{id}/test` | 测试 Provider 连接 | P1 |
| GET | `/api/providers/{id}/models` | 获取 Provider 下模型列表 | P1 |
| POST | `/api/ima/configs` | 添加 IMA 知识库配置 | P1 |
| GET | `/api/ima/configs` | 获取用户 IMA 配置列表 | P1 |
| DELETE | `/api/ima/configs/{id}` | 删除 IMA 配置 | P1 |
| POST | `/api/ima/configs/{id}/test` | 测试知识库连接 | P1 |
| POST | `/api/projects` | 创建项目 + 上传需求文档 | P1 |
| POST | `/api/projects/{id}/run` | 启动流水线执行 | P1 |
| GET | `/api/projects/{id}/status` | 获取流水线执行状态（SSE 推送） | P1 |
| GET | `/api/projects/{id}/download/docx` | 下载 Word 方案文档 | P1 |
| GET | `/api/projects/{id}/download/md` | 下载 Markdown 方案文档 | P1 |

---

> **一句话总结**：PRD 可以进开发。先花 2-3 天补齐 API 契约 + 设计稿 + Mock 接口，然后 4 周内交付 Phase 1 MVP——方案工程师登录 → 连 IMA → 配模型 → 上传需求 → 20 分钟出方案初稿。
