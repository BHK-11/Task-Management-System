ALTER TABLE task_os.workspace_memberships ADD COLUMN notify_assignments BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE task_os.workspace_memberships ADD COLUMN notify_mentions BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE task_os.workspace_memberships ADD COLUMN notify_activity BOOLEAN NOT NULL DEFAULT FALSE;
