ALTER TABLE task_os.task_activity DROP CONSTRAINT ck_task_activity_type;
ALTER TABLE task_os.task_activity ADD CONSTRAINT ck_task_activity_type CHECK (event_type IN (
    'TASK_CREATED', 'TASK_UPDATED', 'STATUS_CHANGED', 'PRIORITY_CHANGED',
    'TASK_COMPLETED', 'TASK_REOPENED', 'TASK_ARCHIVED', 'TASK_RESTORED',
    'TASK_DELETED', 'SUBTASK_ADDED', 'SUBTASK_DELETED', 'CHECKLIST_ADDED', 'CHECKLIST_UPDATED',
    'CHECKLIST_COMPLETED', 'CHECKLIST_REOPENED', 'CHECKLIST_REORDERED', 'CHECKLIST_DELETED',
    'TAG_ASSIGNED', 'TAG_REMOVED', 'TAG_CREATED', 'TAG_RENAMED', 'TAG_DELETED', 'BULK_UPDATED',
    'EISENHOWER_CLASSIFIED', 'GTD_CAPTURED', 'GTD_CLARIFIED', 'ABC_ASSIGNED', 'MOSCOW_ASSIGNED',
    'IMPACT_ASSESSED', 'FROG_SELECTED', 'DAILY_PLAN_UPDATED', 'WEEKLY_REVIEW_COMPLETED'
));

CREATE TABLE task_os.task_productivity (
    task_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    urgent BOOLEAN,
    important BOOLEAN,
    gtd_state VARCHAR(20),
    waiting_for VARCHAR(160),
    follow_up_date DATE,
    abc_category VARCHAR(1),
    abc_rank INTEGER,
    moscow_category VARCHAR(16),
    impact_score INTEGER,
    effort_score INTEGER,
    impact_note VARCHAR(500),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_task_productivity_owner_task FOREIGN KEY (owner_id, task_id)
        REFERENCES task_os.tasks(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_task_productivity_eisenhower CHECK ((urgent IS NULL) = (important IS NULL)),
    CONSTRAINT ck_task_productivity_gtd CHECK (gtd_state IS NULL OR gtd_state IN ('NEXT_ACTION', 'WAITING_FOR', 'SOMEDAY', 'REFERENCE')),
    CONSTRAINT ck_task_productivity_waiting CHECK (gtd_state = 'WAITING_FOR' OR (waiting_for IS NULL AND follow_up_date IS NULL)),
    CONSTRAINT ck_task_productivity_abc CHECK ((abc_category IS NULL AND abc_rank IS NULL) OR (abc_category IN ('A', 'B', 'C') AND abc_rank >= 0)),
    CONSTRAINT uq_task_productivity_abc_rank UNIQUE(owner_id, abc_category, abc_rank),
    CONSTRAINT ck_task_productivity_moscow CHECK (moscow_category IS NULL OR moscow_category IN ('MUST_HAVE', 'SHOULD_HAVE', 'COULD_HAVE', 'WONT_HAVE')),
    CONSTRAINT ck_task_productivity_scores CHECK ((impact_score IS NULL AND effort_score IS NULL) OR (impact_score BETWEEN 1 AND 5 AND effort_score BETWEEN 1 AND 5))
);
CREATE INDEX ix_task_productivity_owner_gtd ON task_os.task_productivity(owner_id, gtd_state, updated_at DESC);
CREATE INDEX ix_task_productivity_owner_abc ON task_os.task_productivity(owner_id, abc_category, abc_rank);
CREATE INDEX ix_task_productivity_owner_moscow ON task_os.task_productivity(owner_id, moscow_category);
CREATE INDEX ix_task_productivity_owner_scores ON task_os.task_productivity(owner_id, impact_score DESC, effort_score);

CREATE TABLE task_os.gtd_inbox_items (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    capture_text VARCHAR(500) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    converted_task_id UUID REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    clarified_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_gtd_inbox_text CHECK (length(trim(capture_text)) > 0),
    CONSTRAINT ck_gtd_inbox_state CHECK (state IN ('OPEN', 'CONVERTED', 'REFERENCE', 'SOMEDAY', 'DISCARDED')),
    CONSTRAINT ck_gtd_inbox_conversion CHECK ((state = 'CONVERTED' AND clarified_at IS NOT NULL) OR (state <> 'CONVERTED' AND converted_task_id IS NULL))
);
CREATE INDEX ix_gtd_inbox_owner_state ON task_os.gtd_inbox_items(owner_id, state, captured_at);

CREATE TABLE task_os.daily_plans (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    planning_date DATE NOT NULL,
    plan_mode VARCHAR(16) NOT NULL,
    frog_task_id UUID REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_daily_plans_owner_date UNIQUE(owner_id, planning_date),
    CONSTRAINT uq_daily_plans_owner_id UNIQUE(owner_id, id),
    CONSTRAINT ck_daily_plans_mode CHECK (plan_mode IN ('IVY_LEE', 'ONE_THREE_FIVE'))
);
CREATE INDEX ix_daily_plans_owner_date ON task_os.daily_plans(owner_id, planning_date DESC);

CREATE TABLE task_os.daily_plan_entries (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    task_id UUID NOT NULL,
    bucket VARCHAR(10) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT fk_daily_plan_entry_plan_owner FOREIGN KEY(owner_id, plan_id)
        REFERENCES task_os.daily_plans(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_plan_entry_task_owner FOREIGN KEY(owner_id, task_id)
        REFERENCES task_os.tasks(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_daily_plan_entry_task UNIQUE(plan_id, task_id),
    CONSTRAINT uq_daily_plan_entry_position UNIQUE(plan_id, bucket, position),
    CONSTRAINT ck_daily_plan_entry_bucket CHECK (bucket IN ('IVY_LEE', 'BIG', 'MEDIUM', 'SMALL')),
    CONSTRAINT ck_daily_plan_entry_position CHECK (position >= 0)
);
CREATE INDEX ix_daily_plan_entries_order ON task_os.daily_plan_entries(plan_id, bucket, position);

CREATE TABLE task_os.weekly_reviews (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    inbox_processed BOOLEAN NOT NULL DEFAULT FALSE,
    next_actions_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    waiting_for_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    someday_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    commitments_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_weekly_reviews_owner_week UNIQUE(owner_id, week_start)
);
CREATE INDEX ix_weekly_reviews_owner_week ON task_os.weekly_reviews(owner_id, week_start DESC);

CREATE TABLE task_os.productivity_preferences (
    owner_id UUID PRIMARY KEY REFERENCES task_os.users(id) ON DELETE CASCADE,
    primary_method VARCHAR(16) NOT NULL DEFAULT 'DAILY_PLAN',
    default_plan_mode VARCHAR(16) NOT NULL DEFAULT 'IVY_LEE',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_productivity_primary_method CHECK (primary_method IN ('EISENHOWER', 'GTD', 'ABC', 'MOSCOW', 'PARETO', 'DAILY_PLAN')),
    CONSTRAINT ck_productivity_default_plan_mode CHECK (default_plan_mode IN ('IVY_LEE', 'ONE_THREE_FIVE'))
);
