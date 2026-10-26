CREATE TABLE task_os.workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_workspaces_name CHECK (length(trim(name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_workspaces_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE task_os.workspace_memberships (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES task_os.workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    max_open_tasks INTEGER,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_workspace_membership_user UNIQUE(workspace_id, user_id),
    CONSTRAINT uq_workspace_membership_id UNIQUE(workspace_id, id),
    CONSTRAINT ck_workspace_membership_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    CONSTRAINT ck_workspace_membership_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REMOVED')),
    CONSTRAINT ck_workspace_membership_capacity CHECK (max_open_tasks IS NULL OR max_open_tasks BETWEEN 1 AND 500)
);
CREATE INDEX ix_workspace_memberships_user ON task_os.workspace_memberships(user_id, status, workspace_id);

CREATE TABLE task_os.workspace_invitations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES task_os.workspaces(id) ON DELETE CASCADE,
    invited_email VARCHAR(254) NOT NULL,
    intended_role VARCHAR(16) NOT NULL,
    invited_by_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_workspace_invitation_role CHECK (intended_role IN ('ADMIN', 'MEMBER', 'VIEWER')),
    CONSTRAINT ck_workspace_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED'))
);
CREATE INDEX ix_workspace_invitations_pending ON task_os.workspace_invitations(workspace_id, invited_email, status, expires_at);

CREATE TABLE task_os.workspace_activity (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES task_os.workspaces(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    event_type VARCHAR(32) NOT NULL,
    summary VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_workspace_activity_type CHECK (event_type IN ('WORKSPACE_CREATED', 'WORKSPACE_UPDATED', 'WORKSPACE_ARCHIVED', 'WORKSPACE_RESTORED', 'MEMBER_JOINED', 'MEMBER_REMOVED', 'ROLE_CHANGED', 'OWNERSHIP_TRANSFERRED', 'INVITATION_CREATED', 'INVITATION_REVOKED', 'PROJECT_SHARED', 'TASK_ASSIGNED', 'TASK_UNASSIGNED', 'TASK_STATUS_CHANGED', 'COMMENT_ADDED'))
);
CREATE INDEX ix_workspace_activity_timeline ON task_os.workspace_activity(workspace_id, occurred_at DESC);

ALTER TABLE task_os.projects ADD COLUMN workspace_id UUID;
ALTER TABLE task_os.projects ADD CONSTRAINT fk_projects_workspace FOREIGN KEY(workspace_id) REFERENCES task_os.workspaces(id) ON DELETE RESTRICT;
CREATE INDEX ix_projects_workspace_active ON task_os.projects(workspace_id, archived_at, updated_at DESC);

ALTER TABLE task_os.tasks ADD COLUMN workspace_id UUID;
ALTER TABLE task_os.tasks ADD CONSTRAINT fk_tasks_workspace FOREIGN KEY(workspace_id) REFERENCES task_os.workspaces(id) ON DELETE RESTRICT;
ALTER TABLE task_os.tasks ADD CONSTRAINT uq_tasks_workspace_id UNIQUE(workspace_id, id);
CREATE INDEX ix_tasks_workspace_status_due ON task_os.tasks(workspace_id, status, due_date);

CREATE TABLE task_os.task_assignments (
    task_id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    assignee_id UUID NOT NULL,
    assigned_by_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_task_assignment_task FOREIGN KEY(workspace_id, task_id) REFERENCES task_os.tasks(workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_task_assignment_assignee FOREIGN KEY(workspace_id, assignee_id) REFERENCES task_os.workspace_memberships(workspace_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_assignment_actor FOREIGN KEY(workspace_id, assigned_by_id) REFERENCES task_os.workspace_memberships(workspace_id, user_id) ON DELETE RESTRICT
);
CREATE INDEX ix_task_assignments_assignee ON task_os.task_assignments(workspace_id, assignee_id, task_id);

CREATE TABLE task_os.task_assignment_history (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    task_id UUID NOT NULL,
    assignee_id UUID,
    actor_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assignment_history_task FOREIGN KEY(workspace_id, task_id) REFERENCES task_os.tasks(workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_history_actor FOREIGN KEY(workspace_id, actor_id) REFERENCES task_os.workspace_memberships(workspace_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_assignment_history_action CHECK (action IN ('ASSIGNED', 'REASSIGNED', 'UNASSIGNED'))
);
CREATE INDEX ix_assignment_history_task ON task_os.task_assignment_history(workspace_id, task_id, occurred_at DESC);

CREATE TABLE task_os.task_comments (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    task_id UUID NOT NULL,
    author_id UUID NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_task_comments_workspace_id UNIQUE(workspace_id, id),
    CONSTRAINT fk_task_comment_task FOREIGN KEY(workspace_id, task_id) REFERENCES task_os.tasks(workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_task_comment_author FOREIGN KEY(workspace_id, author_id) REFERENCES task_os.workspace_memberships(workspace_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_task_comment_body CHECK (length(trim(body)) BETWEEN 1 AND 2000)
);
CREATE INDEX ix_task_comments_timeline ON task_os.task_comments(workspace_id, task_id, created_at);

CREATE TABLE task_os.task_comment_mentions (
    comment_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY(comment_id, user_id),
    CONSTRAINT fk_comment_mention_comment FOREIGN KEY(workspace_id, comment_id) REFERENCES task_os.task_comments(workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_mention_member FOREIGN KEY(workspace_id, user_id) REFERENCES task_os.workspace_memberships(workspace_id, user_id) ON DELETE RESTRICT
);

CREATE TABLE task_os.workspace_notifications (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    task_id UUID,
    notification_type VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_workspace_notification_recipient FOREIGN KEY(workspace_id, recipient_id) REFERENCES task_os.workspace_memberships(workspace_id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_notification_task FOREIGN KEY(workspace_id, task_id) REFERENCES task_os.tasks(workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_workspace_notification_type CHECK (notification_type IN ('ASSIGNMENT', 'MENTION', 'ACTIVITY'))
);
CREATE INDEX ix_workspace_notifications_inbox ON task_os.workspace_notifications(recipient_id, read_at, delivered_at DESC);
