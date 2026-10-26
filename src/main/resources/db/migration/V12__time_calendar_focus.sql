CREATE TABLE task_os.time_blocks (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    task_id UUID,
    title VARCHAR(160) NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    timezone_id VARCHAR(64) NOT NULL,
    notes VARCHAR(1200) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_time_block_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_time_block_task FOREIGN KEY (task_id)
        REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    CONSTRAINT ck_time_block_title CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_time_block_interval CHECK (start_at < end_at),
    CONSTRAINT ck_time_block_status CHECK (status IN ('PLANNED', 'CANCELLED'))
);
CREATE INDEX ix_time_blocks_owner_start ON task_os.time_blocks(owner_id, start_at, end_at);
CREATE INDEX ix_time_blocks_owner_task ON task_os.time_blocks(owner_id, task_id, start_at);

CREATE TABLE task_os.focus_sessions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    task_id UUID,
    mode VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    phase VARCHAR(16) NOT NULL,
    focus_minutes INTEGER NOT NULL,
    short_break_minutes INTEGER NOT NULL,
    long_break_minutes INTEGER NOT NULL,
    completed_cycles INTEGER NOT NULL DEFAULT 0,
    accumulated_phase_seconds BIGINT NOT NULL DEFAULT 0,
    focus_seconds BIGINT NOT NULL DEFAULT 0,
    break_seconds BIGINT NOT NULL DEFAULT 0,
    resumed_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    notes VARCHAR(1200) NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_focus_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_focus_task FOREIGN KEY (task_id)
        REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    CONSTRAINT ck_focus_mode CHECK (mode IN ('POMODORO', 'DEEP_WORK')),
    CONSTRAINT ck_focus_state CHECK (state IN ('RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_focus_phase CHECK (phase IN ('FOCUS', 'SHORT_BREAK', 'LONG_BREAK')),
    CONSTRAINT ck_focus_durations CHECK (focus_minutes BETWEEN 1 AND 240 AND short_break_minutes BETWEEN 1 AND 60 AND long_break_minutes BETWEEN 1 AND 120),
    CONSTRAINT ck_focus_totals CHECK (completed_cycles >= 0 AND accumulated_phase_seconds >= 0 AND focus_seconds >= 0 AND break_seconds >= 0)
);
CREATE INDEX ix_focus_owner_started ON task_os.focus_sessions(owner_id, started_at DESC);
CREATE INDEX ix_focus_owner_state ON task_os.focus_sessions(owner_id, state);

CREATE TABLE task_os.focus_intervals (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    session_id UUID NOT NULL,
    phase VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    planned_seconds INTEGER NOT NULL,
    accumulated_seconds BIGINT NOT NULL DEFAULT 0,
    resumed_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_focus_interval_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_focus_interval_session_owner FOREIGN KEY(owner_id, session_id)
        REFERENCES task_os.focus_sessions(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_focus_interval_phase CHECK (phase IN ('FOCUS', 'SHORT_BREAK', 'LONG_BREAK')),
    CONSTRAINT ck_focus_interval_state CHECK (state IN ('RUNNING', 'PAUSED', 'COMPLETED', 'SKIPPED')),
    CONSTRAINT ck_focus_interval_time CHECK (planned_seconds > 0 AND accumulated_seconds >= 0)
);
CREATE INDEX ix_focus_interval_session ON task_os.focus_intervals(session_id, started_at);

CREATE TABLE task_os.time_entries (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    task_id UUID,
    focus_session_id UUID UNIQUE,
    source VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    description VARCHAR(240) NOT NULL DEFAULT '',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    resumed_at TIMESTAMP WITH TIME ZONE,
    accumulated_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_time_entry_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_time_entry_task FOREIGN KEY (task_id)
        REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    CONSTRAINT fk_time_entry_focus_owner FOREIGN KEY (focus_session_id)
        REFERENCES task_os.focus_sessions(id) ON DELETE SET NULL,
    CONSTRAINT ck_time_entry_source CHECK (source IN ('TIMER', 'MANUAL', 'FOCUS_SESSION')),
    CONSTRAINT ck_time_entry_status CHECK (status IN ('RUNNING', 'PAUSED', 'COMPLETED')),
    CONSTRAINT ck_time_entry_duration CHECK (accumulated_seconds >= 0),
    CONSTRAINT ck_time_entry_end CHECK (ended_at IS NULL OR started_at < ended_at)
);
CREATE INDEX ix_time_entries_owner_started ON task_os.time_entries(owner_id, started_at DESC);
CREATE INDEX ix_time_entries_owner_task ON task_os.time_entries(owner_id, task_id, started_at DESC);

CREATE TABLE task_os.time_entry_segments (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    entry_id UUID NOT NULL,
    focus_interval_id UUID UNIQUE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_segment_entry_owner FOREIGN KEY(owner_id, entry_id)
        REFERENCES task_os.time_entries(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_segment_focus_interval_owner FOREIGN KEY(owner_id, focus_interval_id)
        REFERENCES task_os.focus_intervals(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_segment_interval CHECK (ended_at IS NULL OR started_at < ended_at)
);
CREATE INDEX ix_time_segments_owner_start ON task_os.time_entry_segments(owner_id, started_at, ended_at);
CREATE INDEX ix_time_segments_entry ON task_os.time_entry_segments(entry_id, started_at);

CREATE TABLE task_os.recurrence_rules (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    source_task_id UUID NOT NULL,
    frequency VARCHAR(16) NOT NULL,
    interval_count INTEGER NOT NULL DEFAULT 1,
    timezone_id VARCHAR(64) NOT NULL,
    anchor_date DATE NOT NULL,
    local_time TIME NOT NULL DEFAULT '00:00:00',
    weekdays VARCHAR(32) NOT NULL DEFAULT '',
    month_end_policy VARCHAR(16) NOT NULL DEFAULT 'LAST_DAY',
    end_date DATE,
    occurrence_limit INTEGER,
    next_occurrence_date DATE NOT NULL,
    generated_count INTEGER NOT NULL DEFAULT 0,
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    ended BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_recurrence_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_recurrence_template_owner FOREIGN KEY(owner_id, source_task_id)
        REFERENCES task_os.tasks(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_recurrence_frequency CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),
    CONSTRAINT ck_recurrence_interval CHECK (interval_count BETWEEN 1 AND 365),
    CONSTRAINT ck_recurrence_month_policy CHECK (month_end_policy IN ('SKIP', 'LAST_DAY')),
    CONSTRAINT ck_recurrence_end CHECK (end_date IS NULL OR end_date >= anchor_date),
    CONSTRAINT ck_recurrence_limit CHECK (occurrence_limit IS NULL OR occurrence_limit BETWEEN 1 AND 10000),
    CONSTRAINT ck_recurrence_count CHECK (generated_count >= 0)
);
CREATE INDEX ix_recurrence_due ON task_os.recurrence_rules(paused, next_occurrence_date);
CREATE INDEX ix_recurrence_owner ON task_os.recurrence_rules(owner_id, paused, next_occurrence_date);

CREATE TABLE task_os.recurrence_occurrences (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    task_id UUID,
    occurrence_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_recurrence_occurrence UNIQUE(rule_id, occurrence_date),
    CONSTRAINT fk_occurrence_rule_owner FOREIGN KEY(owner_id, rule_id)
        REFERENCES task_os.recurrence_rules(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_occurrence_task FOREIGN KEY(task_id)
        REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    CONSTRAINT ck_occurrence_status CHECK (status IN ('GENERATED', 'SKIPPED', 'RESCHEDULED'))
);
CREATE INDEX ix_occurrences_owner_date ON task_os.recurrence_occurrences(owner_id, occurrence_date DESC);

CREATE TABLE task_os.reminders (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    task_id UUID,
    time_block_id UUID,
    title VARCHAR(160) NOT NULL,
    scheduled_for TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    dismissed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_reminder_owner_id UNIQUE(owner_id, id),
    CONSTRAINT fk_reminder_task FOREIGN KEY(task_id)
        REFERENCES task_os.tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_reminder_block_owner FOREIGN KEY(owner_id, time_block_id)
        REFERENCES task_os.time_blocks(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_reminder_target CHECK ((task_id IS NOT NULL AND time_block_id IS NULL) OR (task_id IS NULL AND time_block_id IS NOT NULL)),
    CONSTRAINT ck_reminder_status CHECK (status IN ('PENDING', 'DELIVERED', 'DISMISSED', 'CANCELLED')),
    CONSTRAINT ck_reminder_attempt CHECK (attempt_count >= 0)
);
CREATE INDEX ix_reminders_due ON task_os.reminders(status, scheduled_for);
CREATE INDEX ix_reminders_owner_status ON task_os.reminders(owner_id, status, scheduled_for);

CREATE TABLE task_os.in_app_notifications (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    reminder_id UUID NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    task_id UUID,
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_notification_task FOREIGN KEY(task_id)
        REFERENCES task_os.tasks(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_reminder_owner FOREIGN KEY(owner_id, reminder_id)
        REFERENCES task_os.reminders(owner_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_notifications_owner_delivered ON task_os.in_app_notifications(owner_id, delivered_at DESC);
