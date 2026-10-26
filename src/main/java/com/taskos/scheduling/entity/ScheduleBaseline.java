package com.taskos.scheduling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_baselines", schema = "task_os")
public class ScheduleBaseline {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(nullable = false, length = 100, updatable = false) private String name;
    @Column(name = "snapshot_version", nullable = false, updatable = false) private int snapshotVersion;
    @Column(name = "snapshot_json", nullable = false, updatable = false, columnDefinition = "text") private String snapshotJson;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ScheduleBaseline() { }

    public static ScheduleBaseline create(UUID ownerId, UUID projectId, String name, String snapshotJson, Instant now) {
        ScheduleBaseline baseline = new ScheduleBaseline();
        baseline.id = UUID.randomUUID(); baseline.ownerId = ownerId; baseline.projectId = projectId;
        baseline.name = name.trim(); baseline.snapshotVersion = 1; baseline.snapshotJson = snapshotJson; baseline.createdAt = now;
        return baseline;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public int getSnapshotVersion() { return snapshotVersion; }
    public String getSnapshotJson() { return snapshotJson; }
    public Instant getCreatedAt() { return createdAt; }
}
