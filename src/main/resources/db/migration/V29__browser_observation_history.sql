CREATE TABLE task_os.browser_observation_jobs (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    origin VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    page_title VARCHAR(240),
    error_code VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_browser_observation_status CHECK (status IN ('COMPLETED', 'FAILED', 'DENIED'))
);

CREATE INDEX ix_browser_observation_owner_created
    ON task_os.browser_observation_jobs(owner_id, created_at DESC);
