package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sprint_impediments", schema = "task_os")
public class SprintImpediment {
    @Id private UUID id;
    @Column(name = "sprint_id", nullable = false, updatable = false) private UUID sprintId;
    @Column(name = "task_id") private UUID taskId;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(nullable = false, length = 1000) private String description;
    @Column(nullable = false, length = 2000) private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ImpedimentStatus status;
    @Column(name = "reported_at", nullable = false, updatable = false) private Instant reportedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Version private Long version;

    protected SprintImpediment() { }
    public static SprintImpediment create(UUID sprintId, UUID taskId, UUID actorId, String description,
            String notes, Instant now) {
        SprintImpediment item = new SprintImpediment(); item.id = UUID.randomUUID(); item.sprintId = sprintId;
        item.taskId = taskId; item.actorId = actorId; item.description = description.trim();
        item.notes = notes == null ? "" : notes.trim(); item.status = ImpedimentStatus.OPEN; item.reportedAt = now; return item;
    }
    public void resolve(Instant now) { status = ImpedimentStatus.RESOLVED; resolvedAt = now; }
    public UUID getId() { return id; }
    public UUID getSprintId() { return sprintId; }
    public UUID getTaskId() { return taskId; }
    public String getDescription() { return description; }
    public String getNotes() { return notes; }
    public ImpedimentStatus getStatus() { return status; }
    public Instant getReportedAt() { return reportedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
