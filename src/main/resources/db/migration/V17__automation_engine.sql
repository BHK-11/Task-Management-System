CREATE TABLE task_os.automation_rules (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    scope_type VARCHAR(16) NOT NULL,
    scope_id UUID NOT NULL,
    created_by_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    current_version_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    enabled_at TIMESTAMP WITH TIME ZONE,
    disabled_at TIMESTAMP WITH TIME ZONE,
    archived_at TIMESTAMP WITH TIME ZONE,
    last_execution_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_automation_rule_scope CHECK (scope_type IN ('PERSONAL', 'WORKSPACE')),
    CONSTRAINT ck_automation_rule_status CHECK (status IN ('DRAFT', 'ENABLED', 'DISABLED', 'ARCHIVED'))
);
CREATE INDEX ix_automation_rules_scope ON task_os.automation_rules(scope_type, scope_id, updated_at DESC);
CREATE INDEX ix_automation_rules_enabled ON task_os.automation_rules(status, updated_at);

CREATE TABLE task_os.automation_rule_versions (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL REFERENCES task_os.automation_rules(id) ON DELETE RESTRICT,
    version_number INTEGER NOT NULL,
    schema_version INTEGER NOT NULL,
    definition_json VARCHAR(12000) NOT NULL,
    created_by_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_automation_rule_version_number UNIQUE(rule_id, version_number),
    CONSTRAINT uq_automation_rule_version_pair UNIQUE(id, rule_id),
    CONSTRAINT ck_automation_rule_version_positive CHECK (version_number > 0 AND schema_version = 1)
);
ALTER TABLE task_os.automation_rules ADD CONSTRAINT fk_automation_rule_current_version
    FOREIGN KEY(current_version_id, id) REFERENCES task_os.automation_rule_versions(id, rule_id) ON DELETE RESTRICT;

CREATE TABLE task_os.automation_outbox (
    id UUID PRIMARY KEY,
    trigger_type VARCHAR(32) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_id UUID NOT NULL,
    actor_id UUID REFERENCES task_os.users(id) ON DELETE RESTRICT,
    entity_type VARCHAR(32) NOT NULL,
    entity_id UUID,
    payload_json VARCHAR(12000) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    origin_execution_id UUID,
    chain_depth INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    processed_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_automation_outbox_trigger CHECK (trigger_type IN ('TASK_CREATED', 'TASK_STATUS_CHANGED', 'TASK_COMPLETED', 'TASK_REOPENED', 'TASK_PRIORITY_CHANGED', 'TASK_DUE_DATE_CHANGED', 'TASK_ASSIGNED', 'SCHEDULED')),
    CONSTRAINT ck_automation_outbox_scope CHECK (scope_type IN ('PERSONAL', 'WORKSPACE')),
    CONSTRAINT ck_automation_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'DEAD_LETTER')),
    CONSTRAINT ck_automation_outbox_depth CHECK (chain_depth BETWEEN 0 AND 5),
    CONSTRAINT ck_automation_outbox_attempts CHECK (attempt_count >= 0)
);
CREATE INDEX ix_automation_outbox_ready ON task_os.automation_outbox(status, available_at, occurred_at);
CREATE INDEX ix_automation_outbox_scope ON task_os.automation_outbox(scope_type, scope_id, occurred_at);

CREATE TABLE task_os.automation_executions (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL REFERENCES task_os.automation_rules(id) ON DELETE RESTRICT,
    rule_version_id UUID NOT NULL REFERENCES task_os.automation_rule_versions(id) ON DELETE RESTRICT,
    trigger_event_id UUID NOT NULL REFERENCES task_os.automation_outbox(id) ON DELETE RESTRICT,
    scope_type VARCHAR(16) NOT NULL,
    scope_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    initiated_by UUID REFERENCES task_os.users(id) ON DELETE RESTRICT,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64),
    safe_error_summary VARCHAR(240),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_automation_execution_delivery UNIQUE(rule_version_id, trigger_event_id),
    CONSTRAINT ck_automation_execution_scope CHECK (scope_type IN ('PERSONAL', 'WORKSPACE')),
    CONSTRAINT ck_automation_execution_status CHECK (status IN ('PENDING', 'RUNNING', 'WAITING_APPROVAL', 'SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_PERMANENT', 'DEAD_LETTER', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_automation_execution_attempts CHECK (attempt_count >= 0)
);
CREATE INDEX ix_automation_execution_ready ON task_os.automation_executions(status, next_attempt_at, created_at);
CREATE INDEX ix_automation_execution_lease ON task_os.automation_executions(status, lease_expires_at);
CREATE INDEX ix_automation_execution_scope ON task_os.automation_executions(scope_type, scope_id, created_at DESC);
CREATE INDEX ix_automation_execution_rule ON task_os.automation_executions(rule_id, created_at DESC);

CREATE TABLE task_os.automation_step_executions (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES task_os.automation_executions(id) ON DELETE RESTRICT,
    step_index INTEGER NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    result_summary VARCHAR(240),
    error_code VARCHAR(64),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_automation_step_order UNIQUE(execution_id, step_index),
    CONSTRAINT ck_automation_step_index CHECK (step_index BETWEEN 0 AND 9),
    CONSTRAINT ck_automation_step_action CHECK (action_type IN ('CREATE_TASK', 'UPDATE_TASK_STATUS', 'SET_TASK_PRIORITY', 'CREATE_REMINDER')),
    CONSTRAINT ck_automation_step_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_PERMANENT', 'SKIPPED'))
);
CREATE INDEX ix_automation_step_history ON task_os.automation_step_executions(execution_id, step_index);

CREATE TABLE task_os.automation_approvals (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL UNIQUE REFERENCES task_os.automation_executions(id) ON DELETE RESTRICT,
    scope_id UUID NOT NULL,
    requested_by UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    decided_by UUID REFERENCES task_os.users(id) ON DELETE RESTRICT,
    decision_reason VARCHAR(240),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_automation_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_automation_approval_expiration CHECK (expires_at > created_at)
);
CREATE INDEX ix_automation_approval_inbox ON task_os.automation_approvals(scope_id, status, created_at);

CREATE TABLE task_os.automation_schedules (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL UNIQUE REFERENCES task_os.automation_rules(id) ON DELETE RESTRICT,
    rule_version_id UUID NOT NULL REFERENCES task_os.automation_rule_versions(id) ON DELETE RESTRICT,
    frequency VARCHAR(16) NOT NULL,
    day_of_week VARCHAR(12),
    local_time TIME NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    misfire_policy VARCHAR(32) NOT NULL,
    next_fire_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_fire_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_automation_schedule_frequency CHECK (frequency IN ('DAILY', 'WEEKLY')),
    CONSTRAINT ck_automation_schedule_misfire CHECK (misfire_policy IN ('SKIP_MISSED', 'RUN_ONCE_AFTER_RECOVERY')),
    CONSTRAINT ck_automation_schedule_weekly CHECK (frequency <> 'WEEKLY' OR day_of_week IS NOT NULL)
);
CREATE INDEX ix_automation_schedule_due ON task_os.automation_schedules(active, next_fire_at);
