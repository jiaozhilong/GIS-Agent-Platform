-- 预置流程模板表（ddl-auto=validate 模式下由本脚本建表，幂等）
CREATE TABLE IF NOT EXISTS pipeline_templates (
    id              BIGSERIAL PRIMARY KEY,
    template_key    VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    category        VARCHAR(32),
    description     TEXT,
    tool_chain      JSONB,
    estimated_time  VARCHAR(32),
    usage_count     BIGINT       NOT NULL DEFAULT 0,
    builtin         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);
