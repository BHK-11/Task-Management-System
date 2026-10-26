CREATE TABLE task_os.agile_definition_of_done_items (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES task_os.agile_work_items(task_id) ON DELETE CASCADE,
    criterion VARCHAR(500) NOT NULL,
    satisfied BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_agile_dod_position UNIQUE(task_id, position),
    CONSTRAINT ck_agile_dod_criterion CHECK (length(trim(criterion)) > 0),
    CONSTRAINT ck_agile_dod_position CHECK (position >= 0)
);
CREATE INDEX ix_agile_dod_task ON task_os.agile_definition_of_done_items(task_id, position);
