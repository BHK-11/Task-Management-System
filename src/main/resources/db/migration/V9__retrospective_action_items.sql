CREATE TABLE task_os.sprint_action_items (
    id UUID PRIMARY KEY,
    sprint_id UUID NOT NULL REFERENCES task_os.sprints(id) ON DELETE RESTRICT,
    description VARCHAR(1000) NOT NULL,
    task_id UUID REFERENCES task_os.tasks(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sprint_action_position UNIQUE (sprint_id, position),
    CONSTRAINT uq_sprint_action_task UNIQUE (task_id),
    CONSTRAINT ck_sprint_action_description CHECK (length(trim(description)) > 0)
);
CREATE INDEX ix_sprint_action_items ON task_os.sprint_action_items(sprint_id, position);
