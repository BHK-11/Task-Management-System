ALTER TABLE task_os.project_task_placements
    ADD CONSTRAINT uq_project_placement_owner_project_task UNIQUE (owner_id, project_id, task_id);

CREATE TABLE task_os.task_schedule_metadata (
    task_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    planned_duration_days INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_schedule_metadata_owner_task FOREIGN KEY (owner_id, task_id)
        REFERENCES task_os.tasks(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_schedule_duration_days CHECK (planned_duration_days BETWEEN 1 AND 36500)
);

CREATE TABLE task_os.task_dependencies (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    project_id UUID NOT NULL,
    predecessor_task_id UUID NOT NULL,
    successor_task_id UUID NOT NULL,
    dependency_type VARCHAR(3) NOT NULL,
    lag_days INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dependency_project FOREIGN KEY (owner_id, project_id)
        REFERENCES task_os.projects(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_dependency_predecessor FOREIGN KEY (owner_id, project_id, predecessor_task_id)
        REFERENCES task_os.project_task_placements(owner_id, project_id, task_id) ON DELETE CASCADE,
    CONSTRAINT fk_dependency_successor FOREIGN KEY (owner_id, project_id, successor_task_id)
        REFERENCES task_os.project_task_placements(owner_id, project_id, task_id) ON DELETE CASCADE,
    CONSTRAINT uq_dependency_pair UNIQUE (project_id, predecessor_task_id, successor_task_id),
    CONSTRAINT ck_dependency_not_self CHECK (predecessor_task_id <> successor_task_id),
    CONSTRAINT ck_dependency_type CHECK (dependency_type IN ('FS', 'SS', 'FF', 'SF')),
    CONSTRAINT ck_dependency_lag CHECK (lag_days BETWEEN 0 AND 36500)
);
CREATE INDEX ix_dependencies_project_predecessor ON task_os.task_dependencies(project_id, predecessor_task_id);
CREATE INDEX ix_dependencies_project_successor ON task_os.task_dependencies(project_id, successor_task_id);

CREATE TABLE task_os.schedule_baselines (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    project_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    snapshot_version INTEGER NOT NULL DEFAULT 1,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_schedule_baseline_project FOREIGN KEY (owner_id, project_id)
        REFERENCES task_os.projects(owner_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_schedule_baseline_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_schedule_baseline_snapshot_version CHECK (snapshot_version > 0)
);
CREATE INDEX ix_schedule_baselines_project_created ON task_os.schedule_baselines(owner_id, project_id, created_at DESC);
