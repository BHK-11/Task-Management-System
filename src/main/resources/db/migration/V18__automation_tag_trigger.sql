ALTER TABLE task_os.automation_outbox DROP CONSTRAINT ck_automation_outbox_trigger;
ALTER TABLE task_os.automation_outbox ADD CONSTRAINT ck_automation_outbox_trigger
    CHECK (trigger_type IN ('TASK_CREATED', 'TASK_STATUS_CHANGED', 'TASK_COMPLETED', 'TASK_REOPENED',
        'TASK_PRIORITY_CHANGED', 'TASK_DUE_DATE_CHANGED', 'TASK_TAG_ADDED', 'TASK_ASSIGNED', 'SCHEDULED'));
