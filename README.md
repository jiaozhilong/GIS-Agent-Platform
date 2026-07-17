# GIS-Agent Platform

> GIS Agent 平台 — 智能方案生成系统
>
> **用户自配 IMA 知识库 + 用户自配大模型 + 9 件套工具 + 可拼装的流水线画布**

## 🎯 项目简介

面向 GIS 行业解决方案工程师的智能方案生成平台。上传客户需求文档，自动完成需求分析 → 产品匹配 → 方案生成全流程，将方案产出时间从 1-2 天缩短到 2 小时。

## 🏗️ 技术栈

| 层 | 技术 | 说明 |
|---|------|------|
| **前端** | React 19 + TypeScript + Ant Design 5 + React Flow | Vite 构建 |
| **后端** | Java Spring Boot 3.2 + Maven | RESTful API |
| **数据库** | PostgreSQL 14 + pgvector | 结构化数据 + 向量检索 |
| **流程引擎** | 自研 DAG Scheduler | 拓扑排序 + 状态机 |
| **文件导出** | Apache POI | DOCX / PPTX 生成 |

## 📁 项目结构

```
GIS-Agent-Platform/
├── frontend/                # React 前端
│   └── src/
│       ├── api/             # API 客户端 & 接口定义
│       ├── components/      # 通用组件 (MainLayout 等)
│       ├── pages/           # 页面组件
│       │   ├── LoginPage.tsx           # 登录/注册
│       │   ├── ProjectCreatePage.tsx   # 新建方案
│       │   ├── PipelineRunPage.tsx     # 流水线执行
│       │   ├── LlmConfigPage.tsx       # 大模型配置
│       │   └── ImaConfigPage.tsx       # IMA 知识库配置
│       ├── stores/          # Zustand 状态管理
│       └── types/           # TypeScript 类型定义
├── backend/                 # Spring Boot 后端
│   └── src/main/java/com/gisagent/
│       ├── config/          # Security / JWT 配置
│       ├── connector/       # IMA 知识库连接器
│       ├── controller/      # REST 控制器
│       ├── dto/             # 数据传输对象
│       ├── entity/          # JPA 实体
│       ├── repository/      # 数据访问层
│       └── service/         # 业务逻辑层
│   └── src/main/resources/
│       └── db/schema.sql    # 数据库初始化脚本
├── docs/
│   ├── api-spec.yaml        # OpenAPI 3.0 接口契约
│   ├── GIS-Agent-Platform-PRD.md  # 产品需求文档
│   └── GIS-Agent-开发计划.md      # 开发计划
└── README.md
```

## 🚀 快速开始

### 环境要求

- **Java** 17+
- **Node.js** 18+
- **PostgreSQL** 14+ (with pgvector extension)
- **Maven** 3.8+

### 1. 初始化数据库

```bash
createdb gisagent
psql -d gisagent -f backend/src/main/resources/db/schema.sql
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
# API 运行在 http://localhost:8080
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
# 前端运行在 http://localhost:5173
```

### 4. 配置

后端 `application.yml` 关键配置：

```yaml
# 数据库
spring.datasource.url: jdbc:postgresql://localhost:5432/gisagent

# JWT (生产环境务必修改)
app.jwt.secret: ${JWT_SECRET}

# IMA Mock 模式（Phase 1 默认启用）
ima.mock-enabled: true
```

## 📋 Phase 1 MVP 范围

- [x] 用户注册/登录（JWT）
- [x] LLM Provider 管理（添加/测试/删除）
- [x] IMA 知识库配置（连接/用途标注/权重）
- [x] 需求文档上传（PDF/Word/TXT 解析）
- [x] 需求分析 Tool（LLM 提取结构化需求）
- [x] 产品匹配 Tool（IMA 检索 + LLM 推荐）
- [x] Pipeline 引擎（固定顺序执行 + 状态机）
- [x] Word + Markdown 方案导出
- [x] 前端流程接入（上传→运行→轮询状态→下载）

> Phase 1 后端已通过 Maven 编译，前端已通过 Vite 构建。运行需本地 PostgreSQL 14 + 配置 LLM Provider。

## 📖 文档

- [产品需求文档 (PRD)](docs/GIS-Agent-Platform-PRD.md)
- [开发计划](docs/GIS-Agent-开发计划.md)
- [API 契约 (OpenAPI)](docs/api-spec.yaml)

## 📊 开发阶段

| Phase | 内容 | 状态 |
|-------|------|:----:|
| Phase 1 | MVP：核心链路跑通 | 🚧 进行中 |
| Phase 2 | 工具箱完善 + 流程编排 | 📋 计划中 |
| Phase 3 | 智能化增强 | 📋 计划中 |
| Phase 4 | 生态化 | 📋 计划中 |

## 🤝 贡献

GIS-Agent Platform © 2026
