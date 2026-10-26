package com.taskos.scheduling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_schedule_metadata", schema = "task_os")
public class TaskScheduleMetadata {
    @Id @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "planned_duration_days", nullable = false) private int plannedDurationDays;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected TaskScheduleMetadata() { }

    public static TaskScheduleMetadata create(UUID ownerId, UUID taskId, int days, Instant now) {
        TaskScheduleMetadata metadata = new TaskScheduleMetadata();
        metadata.ownerId = ownerId; metadata.taskId = taskId; metadata.plannedDurationDays = days; metadata.updatedAt = now;
        return metadata;
    }

    public void setDuration(int days, Instant now) { plannedDurationDays = days; updatedAt = now; }
    public UUID getTaskId() { return taskId; }
    public UUID getOwnerId() { return ownerId; }
    public int getPlannedDurationDays() { return plannedDurationDays; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
