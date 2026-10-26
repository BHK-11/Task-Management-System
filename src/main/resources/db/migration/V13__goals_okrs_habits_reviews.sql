CREATE TABLE task_os.goals (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    specific_outcome VARCHAR(500) NOT NULL DEFAULT '',
    measurement_definition VARCHAR(240) NOT NULL DEFAULT '',
    why_it_matters VARCHAR(500) NOT NULL DEFAULT '',
    feasibility_notes VARCHAR(500) NOT NULL DEFAULT '',
    category VARCHAR(24) NOT NULL DEFAULT 'PERSONAL',
    metric_type VARCHAR(24) NOT NULL,
    baseline_value NUMERIC(16,4),
    target_value NUMERIC(16,4),
    unit VARCHAR(40) NOT NULL DEFAULT '',
    start_date DATE NOT NULL,
    target_date DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    project_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_goals_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_goals_project FOREIGN KEY(project_id)
        REFERENCES task_os.projects(id) ON DELETE SET NULL,
    CONSTRAINT ck_goals_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_goals_category CHECK (category IN ('CAREER','HEALTH','LEARNING','FINANCE','PERSONAL','PROJECT','OTHER')),
    CONSTRAINT ck_goals_metric CHECK (metric_type IN ('NUMERIC_INCREASE','NUMERIC_DECREASE','PERCENT_COMPLETE','MILESTONE_BASED','TASK_COMPLETION_BASED','MANUAL_STATUS')),
    CONSTRAINT ck_goals_status CHECK (status IN ('DRAFT','ACTIVE','PAUSED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_goals_dates CHECK (target_date IS NULL OR target_date >= start_date),
    CONSTRAINT ck_goals_numeric CHECK (
        metric_type NOT IN ('NUMERIC_INCREASE','NUMERIC_DECREASE','PERCENT_COMPLETE') OR
        (baseline_value IS NOT NULL AND target_value IS NOT NULL AND baseline_value <> target_value))
);
CREATE INDEX ix_goals_owner_status_date ON task_os.goals(owner_id, archived_at, status, target_date);

CREATE TABLE task_os.goal_measurements (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    goal_id UUID NOT NULL,
    recorded_value NUMERIC(16,4) NOT NULL,
    target_snapshot NUMERIC(16,4),
    measurement_date DATE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    CONSTRAINT fk_goal_measurement_goal_owner FOREIGN KEY(owner_id, goal_id)
        REFERENCES task_os.goals(owner_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_goal_measurements_history ON task_os.goal_measurements(owner_id, goal_id, measurement_date DESC, recorded_at DESC);

CREATE TABLE task_os.goal_milestones (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    goal_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    target_date DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    position INTEGER NOT NULL DEFAULT 0,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_goal_milestone_goal_owner FOREIGN KEY(owner_id, goal_id)
        REFERENCES task_os.goals(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_goal_milestone_status CHECK (status IN ('PLANNED','COMPLETED')),
    CONSTRAINT ck_goal_milestone_position CHECK (position >= 0)
);
CREATE INDEX ix_goal_milestones_roadmap ON task_os.goal_milestones(owner_id, goal_id, position);

CREATE TABLE task_os.goal_task_links (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    goal_id UUID NOT NULL,
    task_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_goal_task_links UNIQUE(goal_id, task_id),
    CONSTRAINT fk_goal_task_goal_owner FOREIGN KEY(owner_id, goal_id)
        REFERENCES task_os.goals(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_task_task_owner FOREIGN KEY(owner_id, task_id)
        REFERENCES task_os.tasks(owner_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_goal_task_links_owner_task ON task_os.goal_task_links(owner_id, task_id);

CREATE TABLE task_os.okr_cycles (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_okr_cycles_owner_id UNIQUE(owner_id, id),
    CONSTRAINT ck_okr_cycle_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_okr_cycle_status CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED'))
);
CREATE INDEX ix_okr_cycles_owner_dates ON task_os.okr_cycles(owner_id, start_date, end_date);

CREATE TABLE task_os.objectives (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    cycle_id UUID NOT NULL,
    linked_goal_id UUID,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1200) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    confidence_note VARCHAR(300) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_objectives_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_objective_cycle_owner FOREIGN KEY(owner_id, cycle_id)
        REFERENCES task_os.okr_cycles(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_objective_goal FOREIGN KEY(linked_goal_id)
        REFERENCES task_os.goals(id) ON DELETE SET NULL,
    CONSTRAINT ck_objective_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_objective_status CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED'))
);
CREATE INDEX ix_objectives_owner_cycle ON task_os.objectives(owner_id, cycle_id, status);

CREATE TABLE task_os.key_results (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    objective_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    measurement_unit VARCHAR(40) NOT NULL,
    baseline_value NUMERIC(16,4) NOT NULL,
    target_value NUMERIC(16,4) NOT NULL,
    direction VARCHAR(12) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_key_results_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_key_result_objective_owner FOREIGN KEY(owner_id, objective_id)
        REFERENCES task_os.objectives(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_key_result_direction CHECK (direction IN ('INCREASE','DECREASE')),
    CONSTRAINT ck_key_result_status CHECK (status IN ('ACTIVE','CANCELLED')),
    CONSTRAINT ck_key_result_range CHECK (baseline_value <> target_value AND length(trim(title)) > 0)
);
CREATE INDEX ix_key_results_objective ON task_os.key_results(owner_id, objective_id, status);

CREATE TABLE task_os.key_result_measurements (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    key_result_id UUID NOT NULL,
    recorded_value NUMERIC(16,4) NOT NULL,
    measurement_date DATE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    CONSTRAINT fk_key_result_measurement_owner FOREIGN KEY(owner_id, key_result_id)
        REFERENCES task_os.key_results(owner_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_key_result_measurement_history ON task_os.key_result_measurements(owner_id, key_result_id, measurement_date DESC, recorded_at DESC);

CREATE TABLE task_os.habits (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    start_date DATE NOT NULL,
    timezone_id VARCHAR(64) NOT NULL,
    linked_goal_id UUID,
    linked_task_id UUID,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_habits_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_habit_goal FOREIGN KEY(linked_goal_id)
        REFERENCES task_os.goals(id) ON DELETE SET NULL,
    CONSTRAINT fk_habit_task FOREIGN KEY(linked_task_id)
        REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    CONSTRAINT ck_habit_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_habit_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE INDEX ix_habits_owner_active ON task_os.habits(owner_id, status, archived_at, name);

CREATE TABLE task_os.habit_schedules (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    habit_id UUID NOT NULL,
    effective_from DATE NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    selected_weekdays VARCHAR(20) NOT NULL DEFAULT '',
    target_count INTEGER NOT NULL DEFAULT 1,
    target_quantity NUMERIC(16,4),
    unit VARCHAR(40) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_habit_schedule_owner FOREIGN KEY(owner_id, habit_id)
        REFERENCES task_os.habits(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_habit_schedule_effective UNIQUE(habit_id, effective_from),
    CONSTRAINT ck_habit_schedule_frequency CHECK (frequency IN ('DAILY','SELECTED_DAYS','WEEKLY_TARGET','MONTHLY_TARGET')),
    CONSTRAINT ck_habit_schedule_target CHECK (target_count BETWEEN 1 AND 31 AND (target_quantity IS NULL OR target_quantity >= 0))
);
CREATE INDEX ix_habit_schedule_timeline ON task_os.habit_schedules(owner_id, habit_id, effective_from);

CREATE TABLE task_os.habit_pauses (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    habit_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_habit_pause_owner FOREIGN KEY(owner_id, habit_id)
        REFERENCES task_os.habits(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_habit_pause_dates CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_habit_pause_periods ON task_os.habit_pauses(owner_id, habit_id, start_date, end_date);

CREATE TABLE task_os.habit_check_ins (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    habit_id UUID NOT NULL,
    local_date DATE NOT NULL,
    quantity NUMERIC(16,4),
    performed BOOLEAN NOT NULL,
    notes VARCHAR(600) NOT NULL DEFAULT '',
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_habit_checkin_owner FOREIGN KEY(owner_id, habit_id)
        REFERENCES task_os.habits(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_habit_checkin_date UNIQUE(habit_id, local_date),
    CONSTRAINT ck_habit_checkin_quantity CHECK (quantity IS NULL OR quantity >= 0)
);
CREATE INDEX ix_habit_checkin_history ON task_os.habit_check_ins(owner_id, habit_id, local_date DESC);

CREATE TABLE task_os.reviews (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    review_type VARCHAR(12) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    reflection VARCHAR(3000) NOT NULL DEFAULT '',
    wins VARCHAR(2000) NOT NULL DEFAULT '',
    challenges VARCHAR(2000) NOT NULL DEFAULT '',
    next_steps VARCHAR(2000) NOT NULL DEFAULT '',
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_review_owner_period UNIQUE(owner_id, review_type, period_start),
    CONSTRAINT ck_review_type CHECK (review_type IN ('WEEKLY','MONTHLY')),
    CONSTRAINT ck_review_status CHECK (status IN ('DRAFT','COMPLETED')),
    CONSTRAINT ck_review_dates CHECK (period_end >= period_start)
);
CREATE INDEX ix_review_history ON task_os.reviews(owner_id, period_start DESC);

ALTER TABLE task_os.reminders DROP CONSTRAINT ck_reminder_target;
ALTER TABLE task_os.reminders ADD COLUMN goal_id UUID;
ALTER TABLE task_os.reminders ADD COLUMN habit_id UUID;
ALTER TABLE task_os.reminders
    ADD CONSTRAINT fk_reminder_goal_owner FOREIGN KEY(owner_id, goal_id)
        REFERENCES task_os.goals(owner_id, id) ON DELETE CASCADE;
ALTER TABLE task_os.reminders
    ADD CONSTRAINT fk_reminder_habit_owner FOREIGN KEY(owner_id, habit_id)
        REFERENCES task_os.habits(owner_id, id) ON DELETE CASCADE;
ALTER TABLE task_os.reminders ADD CONSTRAINT ck_reminder_target CHECK (
    (task_id IS NOT NULL AND time_block_id IS NULL AND goal_id IS NULL AND habit_id IS NULL) OR
    (task_id IS NULL AND time_block_id IS NOT NULL AND goal_id IS NULL AND habit_id IS NULL) OR
    (task_id IS NULL AND time_block_id IS NULL AND goal_id IS NOT NULL AND habit_id IS NULL) OR
    (task_id IS NULL AND time_block_id IS NULL AND goal_id IS NULL AND habit_id IS NOT NULL));
CREATE INDEX ix_reminders_owner_goal ON task_os.reminders(owner_id, goal_id, scheduled_for);
CREATE INDEX ix_reminders_owner_habit ON task_os.reminders(owner_id, habit_id, scheduled_for);
