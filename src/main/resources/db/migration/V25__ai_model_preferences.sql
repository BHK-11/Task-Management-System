CREATE TABLE task_os.ai_model_preferences (
    owner_id UUID PRIMARY KEY REFERENCES task_os.users(id) ON DELETE CASCADE,
    mode VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    provider_id VARCHAR(32),
    model_id VARCHAR(128),
    allow_cloud_processing BOOLEAN NOT NULL DEFAULT TRUE,
    allow_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ai_model_preferences_mode CHECK (mode IN (
        'AUTO', 'FAST', 'BALANCED', 'QUALITY', 'ECONOMICAL', 'PRIVATE', 'MANUAL'
    )),
    CONSTRAINT ck_ai_model_preferences_manual CHECK (
        (mode = 'MANUAL' AND provider_id IS NOT NULL AND model_id IS NOT NULL)
        OR (mode <> 'MANUAL')
    )
);
