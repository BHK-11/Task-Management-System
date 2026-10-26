CREATE TABLE task_os.ai_memory_preferences (
    owner_id UUID PRIMARY KEY REFERENCES task_os.users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    consent_version INTEGER NOT NULL DEFAULT 1,
    consented_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE task_os.ai_memories (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    category VARCHAR(24) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    source_type VARCHAR(24) NOT NULL DEFAULT 'USER_CONFIRMED',
    source_note VARCHAR(240),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    supersedes_id UUID REFERENCES task_os.ai_memories(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_ai_memories_category CHECK (category IN ('PREFERENCE', 'WORK_STYLE', 'CONTEXT')),
    CONSTRAINT ck_ai_memories_source CHECK (source_type = 'USER_CONFIRMED'),
    CONSTRAINT ck_ai_memories_status CHECK (status IN ('ACTIVE', 'SUPERSEDED'))
);

CREATE INDEX ix_ai_memories_owner_status ON task_os.ai_memories(owner_id, status, created_at DESC);
CREATE INDEX ix_ai_memories_supersedes ON task_os.ai_memories(supersedes_id);
