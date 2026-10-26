CREATE INDEX ix_tasks_owner_status_due
    ON task_os.tasks (owner_id, status, due_date);

CREATE INDEX ix_tasks_owner_status_completed
    ON task_os.tasks (owner_id, status, completed_at);
