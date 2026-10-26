CREATE TABLE task_os.projects (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL,
    start_date DATE,
    target_end_date DATE,
    color VARCHAR(7) NOT NULL DEFAULT '#4f46e5',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_projects_owner_id UNIQUE(owner_id, id),
    CONSTRAINT ck_projects_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_projects_dates CHECK (target_end_date IS NULL OR start_date IS NULL OR target_end_date >= start_date),
    CONSTRAINT ck_projects_status CHECK (status IN ('PLANNED', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX ix_projects_owner_active ON task_os.projects(owner_id, archived_at, updated_at DESC);
CREATE INDEX ix_projects_owner_status ON task_os.projects(owner_id, status, archived_at, updated_at DESC);

CREATE TABLE task_os.project_board_columns (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    project_id UUID NOT NULL,
    name VARCHAR(48) NOT NULL,
    stage VARCHAR(16) NOT NULL,
    position INTEGER NOT NULL,
    wip_limit INTEGER,
    wip_policy VARCHAR(12) NOT NULL DEFAULT 'NONE',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_columns_owner_project FOREIGN KEY(owner_id, project_id)
        REFERENCES task_os.projects(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_project_columns_project_id UNIQUE(project_id, id),
    CONSTRAINT uq_project_columns_position UNIQUE(project_id, position),
    CONSTRAINT uq_project_columns_name UNIQUE(project_id, name),
    CONSTRAINT ck_project_columns_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_project_columns_stage CHECK (stage IN ('BACKLOG', 'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'ON_HOLD', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_project_columns_position CHECK (position >= 0),
    CONSTRAINT ck_project_columns_wip CHECK (
        (wip_limit IS NULL AND wip_policy = 'NONE') OR
        (wip_limit BETWEEN 1 AND 500 AND wip_policy IN ('ADVISORY', 'STRICT')))
);
CREATE INDEX ix_project_columns_order ON task_os.project_board_columns(project_id, position);

CREATE TABLE task_os.project_milestones (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES task_os.projects(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1200) NOT NULL DEFAULT '',
    target_date DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_project_milestones_project_id UNIQUE(project_id, id),
    CONSTRAINT ck_project_milestones_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_project_milestones_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_project_milestones_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND completed_at IS NULL)),
    CONSTRAINT ck_project_milestones_position CHECK (position >= 0)
);
CREATE INDEX ix_project_milestones_order ON task_os.project_milestones(project_id, position);

CREATE TABLE task_os.project_task_placements (
    task_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    project_id UUID NOT NULL,
    column_id UUID NOT NULL,
    milestone_id UUID,
    position INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_placement_owner_task FOREIGN KEY(owner_id, task_id)
        REFERENCES task_os.tasks(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_project_placement_owner_project FOREIGN KEY(owner_id, project_id)
        REFERENCES task_os.projects(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_project_placement_column FOREIGN KEY(project_id, column_id)
        REFERENCES task_os.project_board_columns(project_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_placement_milestone FOREIGN KEY(project_id, milestone_id)
        REFERENCES task_os.project_milestones(project_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_project_placement_position UNIQUE(project_id, column_id, position),
    CONSTRAINT ck_project_placement_position CHECK (position >= 0)
);
CREATE INDEX ix_project_placements_board ON task_os.project_task_placements(owner_id, project_id, column_id, position);
CREATE INDEX ix_project_placements_milestone ON task_os.project_task_placements(project_id, milestone_id);

CREATE TABLE task_os.project_activity (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    project_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    task_id UUID,
    event_type VARCHAR(32) NOT NULL,
    summary VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_activity_owner_project FOREIGN KEY(owner_id, project_id)
        REFERENCES task_os.projects(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_project_activity_actor FOREIGN KEY(actor_id) REFERENCES task_os.users(id) ON DELETE CASCADE,
    CONSTRAINT ck_project_activity_type CHECK (event_type IN (
        'PROJECT_CREATED', 'PROJECT_UPDATED', 'PROJECT_STATUS_CHANGED', 'PROJECT_ARCHIVED', 'PROJECT_RESTORED',
        'TASK_ADDED', 'TASK_REMOVED', 'TASK_MOVED', 'TASK_STATUS_SYNCED',
        'COLUMN_CREATED', 'COLUMN_UPDATED', 'COLUMN_REMOVED', 'MILESTONE_CREATED', 'MILESTONE_UPDATED',
        'MILESTONE_STATUS_CHANGED', 'MILESTONE_REMOVED', 'TASK_MILESTONE_CHANGED', 'TEMPLATE_APPLIED', 'TEMPLATE_SAVED'))
);
CREATE INDEX ix_project_activity_timeline ON task_os.project_activity(owner_id, project_id, occurred_at DESC);

CREATE TABLE task_os.project_templates (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1200) NOT NULL DEFAULT '',
    schema_version INTEGER NOT NULL DEFAULT 1,
    definition_json VARCHAR(24000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_project_templates_owner_name UNIQUE(owner_id, name),
    CONSTRAINT ck_project_templates_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_project_templates_version CHECK (schema_version = 1),
    CONSTRAINT ck_project_templates_definition CHECK (length(definition_json) > 1)
);
CREATE INDEX ix_project_templates_owner ON task_os.project_templates(owner_id, updated_at DESC);
