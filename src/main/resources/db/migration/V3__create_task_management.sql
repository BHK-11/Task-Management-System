CREATE TABLE task_os.tasks (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users (id) ON DELETE CASCADE,
    parent_id UUID,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(6000) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    start_date DATE,
    due_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tasks_owner_id UNIQUE (owner_id, id),
    CONSTRAINT fk_tasks_parent_owner FOREIGN KEY (owner_id, parent_id)
        REFERENCES task_os.tasks (owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_tasks_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_tasks_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_tasks_dates CHECK (due_date IS NULL OR start_date IS NULL OR due_date >= start_date),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_tasks_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL))
);

CREATE INDEX ix_tasks_owner_created ON task_os.tasks (owner_id, created_at DESC, id);
CREATE INDEX ix_tasks_owner_status ON task_os.tasks (owner_id, status, updated_at DESC);
CREATE INDEX ix_tasks_owner_priority ON task_os.tasks (owner_id, priority, updated_at DESC);
CREATE INDEX ix_tasks_owner_due ON task_os.tasks (owner_id, due_date, id);
CREATE INDEX ix_tasks_owner_archived ON task_os.tasks (owner_id, archived_at, updated_at DESC);
CREATE INDEX ix_tasks_owner_parent ON task_os.tasks (owner_id, parent_id);

CREATE TABLE task_os.tags (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users (id) ON DELETE CASCADE,
    name VARCHAR(32) NOT NULL,
    color VARCHAR(16) NOT NULL DEFAULT 'SLATE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tags_owner_name UNIQUE (owner_id, name),
    CONSTRAINT ck_tags_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_tags_color CHECK (color IN ('SLATE', 'BLUE', 'TEAL', 'AMBER', 'ROSE', 'VIOLET'))
);
CREATE INDEX ix_tags_owner_name ON task_os.tags (owner_id, name);

CREATE TABLE task_os.task_tag_assignments (
    task_id UUID NOT NULL REFERENCES task_os.tasks (id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES task_os.tags (id) ON DELETE CASCADE,
    CONSTRAINT pk_task_tag_assignments PRIMARY KEY (task_id, tag_id)
);
CREATE INDEX ix_task_tag_assignments_tag ON task_os.task_tag_assignments (tag_id, task_id);

CREATE TABLE task_os.task_checklist_items (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES task_os.tasks (id) ON DELETE CASCADE,
    item_text VARCHAR(240) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_task_checklist_text CHECK (length(trim(item_text)) > 0),
    CONSTRAINT ck_task_checklist_completion CHECK (
        (completed = TRUE AND completed_at IS NOT NULL)
        OR (completed = FALSE AND completed_at IS NULL))
);
CREATE INDEX ix_task_checklist_order ON task_os.task_checklist_items (task_id, position, created_at);

CREATE TABLE task_os.task_activity (
    id UUID PRIMARY KEY,
    task_id UUID,
    owner_id UUID NOT NULL REFERENCES task_os.users (id) ON DELETE CASCADE,
    actor_id UUID REFERENCES task_os.users (id) ON DELETE SET NULL,
    event_type VARCHAR(32) NOT NULL,
    summary VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_task_activity_type CHECK (event_type IN (
        'TASK_CREATED', 'TASK_UPDATED', 'STATUS_CHANGED', 'PRIORITY_CHANGED',
        'TASK_COMPLETED', 'TASK_REOPENED', 'TASK_ARCHIVED', 'TASK_RESTORED',
        'TASK_DELETED', 'SUBTASK_ADDED', 'SUBTASK_DELETED', 'CHECKLIST_ADDED', 'CHECKLIST_UPDATED',
        'CHECKLIST_COMPLETED', 'CHECKLIST_REOPENED', 'CHECKLIST_REORDERED',
        'CHECKLIST_DELETED', 'TAG_ASSIGNED', 'TAG_REMOVED', 'TAG_CREATED', 'TAG_RENAMED', 'TAG_DELETED', 'BULK_UPDATED'))
);
CREATE INDEX ix_task_activity_task_time ON task_os.task_activity (owner_id, task_id, occurred_at DESC);
CREATE INDEX ix_task_activity_owner_time ON task_os.task_activity (owner_id, occurred_at DESC);
