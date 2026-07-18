-- ============================================================
-- GIS-Agent Platform — 数据库初始化脚本
-- 适用: PostgreSQL 14 + pgvector
-- ============================================================

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(256) NOT NULL,               -- BCrypt hash
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- LLM Provider 配置（用户级）
-- ============================================================
CREATE TABLE IF NOT EXISTS llm_providers (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name             VARCHAR(128) NOT NULL,          -- e.g., "GPT-4o", "DeepSeek-V3"
    provider_type    VARCHAR(32)  NOT NULL DEFAULT 'openai_compatible', -- openai_compatible | local
    endpoint         VARCHAR(512) NOT NULL,          -- API 地址
    api_key_encrypted VARCHAR(512),                  -- 加密存储的 API Key
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- LLM 模型列表（每个 Provider 下可配置多个模型）
-- ============================================================
CREATE TABLE IF NOT EXISTS llm_models (
    id              BIGSERIAL PRIMARY KEY,
    provider_id     BIGINT       NOT NULL REFERENCES llm_providers(id) ON DELETE CASCADE,
    model_name      VARCHAR(128) NOT NULL,           -- e.g., "gpt-4o", "deepseek-chat"
    context_window  INT          NOT NULL DEFAULT 8192,
    max_tokens      INT          NOT NULL DEFAULT 4096,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- IMA 知识库配置（用户级）
-- ============================================================
CREATE TABLE IF NOT EXISTS ima_kb_configs (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kb_id         VARCHAR(128) NOT NULL,             -- IMA 知识库 ID
    kb_name       VARCHAR(256) NOT NULL,             -- 知识库名称
    kb_type       VARCHAR(32)  NOT NULL DEFAULT 'subscribed', -- subscribed | owned
    purpose       VARCHAR(32)  NOT NULL DEFAULT 'general',    -- product_doc | case_lib | industry_standard | competitor | general
    search_weight FLOAT        NOT NULL DEFAULT 0.5, -- 检索权重 0-1
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, kb_id)
);

-- ============================================================
-- 项目表（一次方案生成 = 一个项目）
-- ============================================================
CREATE TABLE IF NOT EXISTS projects (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(256) NOT NULL,
    description     TEXT,
    template_id     VARCHAR(64),                     -- 使用的预置模板 ID（MVP 可为空）
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT', -- DRAFT | RUNNING | COMPLETED | FAILED
    context_json    JSONB,                            -- 共享上下文数据 (Context Bus)
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 项目需求文档（上传的原始文件）
-- ============================================================
CREATE TABLE IF NOT EXISTS project_documents (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    file_name       VARCHAR(512) NOT NULL,
    file_path       VARCHAR(1024) NOT NULL,          -- 本地存储路径
    file_type       VARCHAR(32)  NOT NULL,           -- PDF | DOCX | TXT
    file_size       BIGINT       NOT NULL DEFAULT 0,
    uploaded_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 流水线执行记录
-- ============================================================
CREATE TABLE IF NOT EXISTS pipeline_runs (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    template_id     VARCHAR(64),                     -- 使用的模板 ID
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING', -- PENDING | RUNNING | SUCCESS | FAILED | PARTIAL
    context_json    JSONB,                            -- 执行时的上下文快照
    error_message   TEXT,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 工具执行记录
-- ============================================================
CREATE TABLE IF NOT EXISTS tool_executions (
    id              BIGSERIAL PRIMARY KEY,
    pipeline_run_id BIGINT       NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    tool_type       VARCHAR(64)  NOT NULL,           -- REQUIREMENT_ANALYSIS | PRODUCT_MATCHING | CASE_RECOMMEND | ...
    tool_order      INT          NOT NULL DEFAULT 0, -- 执行顺序
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING', -- PENDING | RUNNING | SUCCESS | FAILED | SKIPPED
    llm_provider_id BIGINT       REFERENCES llm_providers(id),
    llm_model       VARCHAR(128),                    -- 使用的具体模型名
    input_json      JSONB,
    output_json     JSONB,
    error_message   TEXT,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 导出文件记录
-- ============================================================
CREATE TABLE IF NOT EXISTS exports (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    file_type       VARCHAR(16)  NOT NULL,           -- DOCX | MD | PPTX
    file_name       VARCHAR(512) NOT NULL,
    file_path       VARCHAR(1024) NOT NULL,
    file_size       BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 索引
-- ============================================================
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_llm_providers_user ON llm_providers(user_id);
CREATE INDEX idx_ima_kb_configs_user ON ima_kb_configs(user_id);
CREATE INDEX idx_ima_kb_configs_purpose ON ima_kb_configs(user_id, purpose);
CREATE INDEX idx_projects_user ON projects(user_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_pipeline_runs_project ON pipeline_runs(project_id);
CREATE INDEX idx_tool_executions_pipeline ON tool_executions(pipeline_run_id);
CREATE INDEX idx_exports_project ON exports(project_id);

-- ============================================================
-- 更新时间触发器
-- ============================================================
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_llm_providers_updated BEFORE UPDATE ON llm_providers
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_ima_kb_configs_updated BEFORE UPDATE ON ima_kb_configs
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_projects_updated BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- ============================================================
-- 知识库同步游标（P3-1 知识库自动感知）
-- ============================================================
CREATE TABLE IF NOT EXISTS kb_sync_states (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kb_id        VARCHAR(128) NOT NULL,
    last_cursor  TIMESTAMP,
    last_sync_at TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, kb_id)
);

-- 项目表：知识库更新感知标记（P3-1）
ALTER TABLE projects ADD COLUMN IF NOT EXISTS kb_dirty BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS kb_dirty_note TEXT;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS kb_dirty_since TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_kb_sync_states_user ON kb_sync_states(user_id);
CREATE INDEX IF NOT EXISTS idx_projects_kb_dirty ON projects(user_id, kb_dirty);

-- ============================================================
-- 方案版本快照（P4-3 版本管理）
-- ============================================================
CREATE TABLE IF NOT EXISTS project_versions (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    version_no      INT          NOT NULL DEFAULT 1,         -- 项目内自增版本号
    title           VARCHAR(128),
    trigger_type    VARCHAR(32),                            -- AUTO_RUN | KB_RERUN | MANUAL
    context_json    JSONB,                                  -- 完整 Context Bus 快照
    solution_text   TEXT,                                   -- 方案正文冗余副本（列表预览/对比）
    note            TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_project_versions_project ON project_versions(project_id);

-- ============================================================
-- 模板市场（P4-1 社区共享 / 点赞 / 收藏）
-- ============================================================
ALTER TABLE pipeline_templates ADD COLUMN IF NOT EXISTS owner_id      BIGINT;
ALTER TABLE pipeline_templates ADD COLUMN IF NOT EXISTS like_count    BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pipeline_templates ADD COLUMN IF NOT EXISTS favorite_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pipeline_templates ADD COLUMN IF NOT EXISTS published     BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS template_likes (
    id          BIGSERIAL PRIMARY KEY,
    template_id BIGINT       NOT NULL REFERENCES pipeline_templates(id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(template_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_template_likes_tpl ON template_likes(template_id);

CREATE TABLE IF NOT EXISTS template_favorites (
    id          BIGSERIAL PRIMARY KEY,
    template_id BIGINT       NOT NULL REFERENCES pipeline_templates(id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(template_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_template_favorites_tpl ON template_favorites(template_id);


