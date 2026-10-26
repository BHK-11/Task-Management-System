CREATE TABLE task_os.agile_project_settings (
    project_id UUID PRIMARY KEY REFERENCES task_os.projects(id) ON DELETE RESTRICT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    definition_of_done VARCHAR(4000) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_agile_definition_of_done CHECK (length(definition_of_done) <= 4000)
);

CREATE TABLE task_os.epics (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES task_os.projects(id) ON DELETE RESTRICT,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    target_date DATE,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_epics_project_id UNIQUE(project_id, id),
    CONSTRAINT ck_epics_title CHECK (length(trim(title)) > 0)
);
CREATE INDEX ix_epics_project_active ON task_os.epics(project_id, archived_at, title);

CREATE TABLE task_os.agile_work_items (
    task_id UUID PRIMARY KEY REFERENCES task_os.tasks(id) ON DELETE RESTRICT,
    project_id UUID NOT NULL REFERENCES task_os.projects(id) ON DELETE RESTRICT,
    epic_id UUID REFERENCES task_os.epics(id) ON DELETE SET NULL,
    work_item_type VARCHAR(16) NOT NULL DEFAULT 'TASK',
    story_points INTEGER,
    backlog_position INTEGER NOT NULL,
    definition_of_done VARCHAR(2000) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_agile_backlog_position UNIQUE(project_id, backlog_position),
    CONSTRAINT ck_agile_item_type CHECK (work_item_type IN ('STORY', 'TASK', 'BUG', 'SPIKE')),
    CONSTRAINT ck_agile_story_points CHECK (story_points IS NULL OR story_points IN (0, 1, 2, 3, 5, 8, 13, 20)),
    CONSTRAINT ck_agile_backlog_position CHECK (backlog_position >= 0),
    CONSTRAINT ck_agile_item_dod CHECK (length(definition_of_done) <= 2000)
);
CREATE INDEX ix_agile_work_items_project_order ON task_os.agile_work_items(project_id, backlog_position);
CREATE INDEX ix_agile_work_items_epic ON task_os.agile_work_items(project_id, epic_id);

CREATE TABLE task_os.acceptance_criteria (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES task_os.agile_work_items(task_id) ON DELETE CASCADE,
    criterion VARCHAR(500) NOT NULL,
    satisfied BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_acceptance_criteria_position UNIQUE(task_id, position),
    CONSTRAINT ck_acceptance_criterion CHECK (length(trim(criterion)) > 0),
    CONSTRAINT ck_acceptance_position CHECK (position >= 0)
);
CREATE INDEX ix_acceptance_criteria_task ON task_os.acceptance_criteria(task_id, position);

CREATE TABLE task_os.sprints (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES task_os.projects(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    goal VARCHAR(500) NOT NULL,
    planning_notes VARCHAR(3000) NOT NULL DEFAULT '',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    capacity_points INTEGER,
    baseline_points INTEGER NOT NULL DEFAULT 0,
    baseline_item_count INTEGER NOT NULL DEFAULT 0,
    delivered_points INTEGER NOT NULL DEFAULT 0,
    final_scope_points INTEGER NOT NULL DEFAULT 0,
    final_remaining_points INTEGER NOT NULL DEFAULT 0,
    completed_item_count INTEGER NOT NULL DEFAULT 0,
    incomplete_item_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_sprints_project_id UNIQUE(project_id, id),
    CONSTRAINT ck_sprints_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_sprints_goal CHECK (length(trim(goal)) > 0),
    CONSTRAINT ck_sprints_dates CHECK (end_date >= start_date AND end_date <= start_date + 90),
    CONSTRAINT ck_sprints_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_sprints_capacity CHECK (capacity_points IS NULL OR capacity_points >= 0),
    CONSTRAINT ck_sprints_metrics CHECK (baseline_points >= 0 AND baseline_item_count >= 0 AND delivered_points >= 0
        AND final_scope_points >= 0 AND final_remaining_points >= 0 AND completed_item_count >= 0 AND incomplete_item_count >= 0),
    CONSTRAINT ck_sprints_started CHECK ((status IN ('ACTIVE', 'COMPLETED') AND started_at IS NOT NULL)
        OR status IN ('PLANNED', 'CANCELLED')),
    CONSTRAINT ck_sprints_completed CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL))
);
CREATE INDEX ix_sprints_project_status_dates ON task_os.sprints(project_id, status, start_date, end_date);

CREATE TABLE task_os.sprint_memberships (
    id UUID PRIMARY KEY,
    sprint_id UUID NOT NULL REFERENCES task_os.sprints(id) ON DELETE RESTRICT,
    task_id UUID NOT NULL REFERENCES task_os.agile_work_items(task_id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL,
    position INTEGER NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_sprint_membership_task UNIQUE(sprint_id, task_id),
    CONSTRAINT ck_sprint_membership_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'INCOMPLETE', 'CARRIED', 'REMOVED')),
    CONSTRAINT ck_sprint_membership_position CHECK (position >= 0)
);
CREATE INDEX ix_sprint_memberships_sprint ON task_os.sprint_memberships(sprint_id, status, position);
CREATE INDEX ix_sprint_memberships_task ON task_os.sprint_memberships(task_id, status);

CREATE TABLE task_os.sprint_baseline_items (
    id UUID PRIMARY KEY,
    sprint_id UUID NOT NULL REFERENCES task_os.sprints(id) ON DELETE RESTRICT,
    task_id UUID NOT NULL REFERENCES task_os.agile_work_items(task_id) ON DELETE RESTRICT,
    title VARCHAR(160) NOT NULL,
    story_points INTEGER,
    task_status VARCHAR(16) NOT NULL,
    CONSTRAINT uq_sprint_baseline_task UNIQUE(sprint_id, task_id),
    CONSTRAINT ck_sprint_baseline_points CHECK (story_points IS NULL OR story_points IN (0, 1, 2, 3, 5, 8, 13, 20))
);

CREATE TABLE task_os.sprint_events (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES task_os.projects(id) ON DELETE RESTRICT,
    sprint_id UUID REFERENCES task_os.sprints(id) ON DELETE RESTRICT,
    task_id UUID REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    actor_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    event_type VARCHAR(32) NOT NULL,
    summary VARCHAR(240) NOT NULL,
    previous_value VARCHAR(500),
    new_value VARCHAR(500),
    reason VARCHAR(500),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sprint_events_type CHECK (event_type IN (
        'SCRUM_ENABLED', 'BACKLOG_ITEM_UPDATED', 'BACKLOG_REORDERED', 'CRITERION_ADDED', 'CRITERION_UPDATED',
        'CRITERION_REMOVED', 'EPIC_CREATED', 'EPIC_UPDATED', 'EPIC_ARCHIVED', 'SPRINT_CREATED', 'SPRINT_UPDATED',
        'SPRINT_STARTED', 'SPRINT_COMPLETED', 'SPRINT_CANCELLED', 'SCOPE_ADDED', 'SCOPE_REMOVED',
        'ESTIMATE_CHANGED', 'TASK_COMPLETED', 'TASK_REOPENED', 'DAILY_NOTE_ADDED', 'IMPEDIMENT_REPORTED', 'IMPEDIMENT_RESOLVED'))
);
CREATE INDEX ix_sprint_events_project_timeline ON task_os.sprint_events(project_id, occurred_at DESC);
CREATE INDEX ix_sprint_events_sprint_timeline ON task_os.sprint_events(sprint_id, occurred_at);

CREATE TABLE task_os.sprint_ceremonies (
    sprint_id UUID PRIMARY KEY REFERENCES task_os.sprints(id) ON DELETE RESTRICT,
    goal_outcome VARCHAR(5000) NOT NULL DEFAULT '',
    delivered_work VARCHAR(5000) NOT NULL DEFAULT '',
    incomplete_work VARCHAR(5000) NOT NULL DEFAULT '',
    demo_notes VARCHAR(5000) NOT NULL DEFAULT '',
    stakeholder_feedback VARCHAR(5000) NOT NULL DEFAULT '',
    accepted_items VARCHAR(5000) NOT NULL DEFAULT '',
    needs_rework_items VARCHAR(5000) NOT NULL DEFAULT '',
    review_follow_ups VARCHAR(5000) NOT NULL DEFAULT '',
    went_well VARCHAR(5000) NOT NULL DEFAULT '',
    could_improve VARCHAR(5000) NOT NULL DEFAULT '',
    try_next VARCHAR(5000) NOT NULL DEFAULT '',
    retrospective_actions VARCHAR(5000) NOT NULL DEFAULT '',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE task_os.sprint_daily_notes (
    id UUID PRIMARY KEY,
    sprint_id UUID NOT NULL REFERENCES task_os.sprints(id) ON DELETE RESTRICT,
    actor_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    progress VARCHAR(2000) NOT NULL DEFAULT '',
    next_steps VARCHAR(2000) NOT NULL DEFAULT '',
    blockers VARCHAR(2000) NOT NULL DEFAULT '',
    notes VARCHAR(3000) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_sprint_daily_notes_timeline ON task_os.sprint_daily_notes(sprint_id, created_at DESC);

CREATE TABLE task_os.sprint_impediments (
    id UUID PRIMARY KEY,
    sprint_id UUID NOT NULL REFERENCES task_os.sprints(id) ON DELETE RESTRICT,
    task_id UUID REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    actor_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE RESTRICT,
    description VARCHAR(1000) NOT NULL,
    notes VARCHAR(2000) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_sprint_impediment_description CHECK (length(trim(description)) > 0),
    CONSTRAINT ck_sprint_impediment_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_sprint_impediment_resolution CHECK ((status = 'RESOLVED' AND resolved_at IS NOT NULL)
        OR (status = 'OPEN' AND resolved_at IS NULL))
);
CREATE INDEX ix_sprint_impediments_open ON task_os.sprint_impediments(sprint_id, status, reported_at);
