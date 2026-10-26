ALTER TABLE task_os.tasks ADD COLUMN subtask_position INTEGER;

UPDATE task_os.tasks child
SET subtask_position = (
    SELECT COUNT(*)
    FROM task_os.tasks prior
    WHERE prior.owner_id = child.owner_id
      AND prior.parent_id = child.parent_id
      AND prior.created_at < child.created_at
)
WHERE child.parent_id IS NOT NULL;

ALTER TABLE task_os.tasks ADD CONSTRAINT ck_tasks_subtask_position
    CHECK ((parent_id IS NULL AND subtask_position IS NULL)
        OR (parent_id IS NOT NULL AND subtask_position >= 0));

CREATE INDEX ix_tasks_owner_parent_position
    ON task_os.tasks (owner_id, parent_id, subtask_position);
