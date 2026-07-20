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

-- 兼容字段升级：LlmProvider 新增 model 列（幂等）
ALTER TABLE llm_providers ADD COLUMN IF NOT EXISTS model VARCHAR(128);

-- 计费纵深：组织配额与账期账单（P8-1，与 db/schema.sql 保持一致，幂等）
CREATE TABLE IF NOT EXISTS usage_quotas (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    token_limit     BIGINT       NOT NULL,
    warn_threshold  INT          NOT NULL DEFAULT 80,
    alerted_month   VARCHAR(16),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(organization_id)
);

CREATE TABLE IF NOT EXISTS invoices (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    period_month    VARCHAR(16)  NOT NULL,
    run_count       BIGINT       NOT NULL DEFAULT 0,
    input_tokens    BIGINT       NOT NULL DEFAULT 0,
    output_tokens   BIGINT       NOT NULL DEFAULT 0,
    total_tokens    BIGINT       NOT NULL DEFAULT 0,
    estimated_cost  DOUBLE PRECISION NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    settled_at      TIMESTAMP,
    UNIQUE(organization_id, period_month)
);

CREATE INDEX IF NOT EXISTS idx_usage_quotas_org ON usage_quotas(organization_id);
CREATE INDEX IF NOT EXISTS idx_invoices_org ON invoices(organization_id);
CREATE INDEX IF NOT EXISTS idx_invoices_month ON invoices(period_month);

