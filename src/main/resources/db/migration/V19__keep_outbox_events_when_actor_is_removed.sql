ALTER TABLE task_os.automation_outbox DROP CONSTRAINT IF EXISTS CONSTRAINT_C266;
ALTER TABLE task_os.automation_outbox DROP CONSTRAINT IF EXISTS automation_outbox_actor_id_fkey;
ALTER TABLE task_os.automation_outbox DROP CONSTRAINT IF EXISTS fk_automation_outbox_actor;
ALTER TABLE task_os.automation_outbox ADD CONSTRAINT fk_automation_outbox_actor
    FOREIGN KEY (actor_id) REFERENCES task_os.users(id) ON DELETE SET NULL;
