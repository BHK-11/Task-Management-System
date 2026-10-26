CREATE TABLE task_os.ai_plans (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    original_request VARCHAR(4000) NOT NULL,
    summary VARCHAR(1200) NOT NULL,
    detected_language VARCHAR(16) NOT NULL,
    clarifications VARCHAR(1200) NOT NULL DEFAULT '',
    provider_model VARCHAR(128) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_plan_language CHECK (detected_language IN ('en', 'hi', 'hinglish', 'mixed', 'unknown')),
    CONSTRAINT ck_ai_plan_status CHECK (status IN ('PENDING_APPROVAL', 'COMPLETED', 'PARTIALLY_COMPLETED', 'REJECTED')),
    CONSTRAINT ck_ai_plan_usage CHECK ((input_tokens IS NULL OR input_tokens >= 0) AND (output_tokens IS NULL OR output_tokens >= 0))
);

CREATE INDEX ix_ai_plans_owner_created ON task_os.ai_plans(owner_id, created_at DESC);

CREATE TABLE task_os.ai_plan_steps (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES task_os.ai_plans(id) ON DELETE CASCADE,
    step_index INTEGER NOT NULL,
    action_type VARCHAR(24) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(6000) NOT NULL DEFAULT '',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    due_date DATE,
    dependencies VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(24) NOT NULL,
    result_task_id UUID REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    result_summary VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT uq_ai_plan_step_index UNIQUE(plan_id, step_index),
    CONSTRAINT ck_ai_step_index CHECK (step_index >= 0),
    CONSTRAINT ck_ai_step_action CHECK (action_type IN ('CREATE_TASK', 'UNSUPPORTED')),
    CONSTRAINT ck_ai_step_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_ai_step_status CHECK (status IN ('PENDING', 'COMPLETED', 'UNSUPPORTED', 'BLOCKED'))
);

CREATE INDEX ix_ai_plan_steps_plan_order ON task_os.ai_plan_steps(plan_id, step_index);
