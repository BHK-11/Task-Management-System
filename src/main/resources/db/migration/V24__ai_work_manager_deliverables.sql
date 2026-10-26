ALTER TABLE task_os.ai_plans ALTER COLUMN original_request TYPE VARCHAR(20000);
ALTER TABLE task_os.ai_plans ADD COLUMN execution_lease_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE task_os.ai_plans DROP CONSTRAINT ck_ai_plan_status;
ALTER TABLE task_os.ai_plans ADD CONSTRAINT ck_ai_plan_status CHECK (status IN (
    'PENDING_APPROVAL', 'QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'COMPLETED',
    'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED', 'REJECTED'
));

ALTER TABLE task_os.ai_plan_steps ADD COLUMN content VARCHAR(12000) NOT NULL DEFAULT '';
ALTER TABLE task_os.ai_plan_steps ADD COLUMN result_artifact_id UUID;
ALTER TABLE task_os.ai_plan_steps DROP CONSTRAINT ck_ai_step_action;
ALTER TABLE task_os.ai_plan_steps ADD CONSTRAINT ck_ai_step_action CHECK (action_type IN (
    'CREATE_TASK', 'GENERATE_DOCX', 'GENERATE_PDF', 'GENERATE_XLSX', 'GENERATE_PPTX',
    'DRAFT_EMAIL', 'DRAFT_MESSAGE', 'UNSUPPORTED'
));
ALTER TABLE task_os.ai_plan_steps DROP CONSTRAINT ck_ai_step_status;
ALTER TABLE task_os.ai_plan_steps ADD CONSTRAINT ck_ai_step_status CHECK (
    status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'UNSUPPORTED', 'BLOCKED')
);

CREATE TABLE task_os.ai_artifacts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES task_os.ai_plans(id) ON DELETE CASCADE,
    step_id UUID NOT NULL REFERENCES task_os.ai_plan_steps(id) ON DELETE CASCADE,
    file_name VARCHAR(160) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    storage_key VARCHAR(80) NOT NULL UNIQUE,
    size BIGINT NOT NULL CHECK (size > 0 AND size <= 10485760),
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
ALTER TABLE task_os.ai_plan_steps ADD CONSTRAINT fk_ai_step_result_artifact
    FOREIGN KEY (result_artifact_id) REFERENCES task_os.ai_artifacts(id) ON DELETE SET NULL;
CREATE INDEX ix_ai_artifacts_owner_plan ON task_os.ai_artifacts(owner_id, plan_id, created_at);
CREATE INDEX ix_ai_plans_execution_queue ON task_os.ai_plans(status, execution_lease_until, created_at);
