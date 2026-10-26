ALTER TABLE task_os.ai_memories
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
