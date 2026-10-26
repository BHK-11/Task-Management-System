package com.taskos.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_activity", schema = "task_os")
public class ProjectActivity {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(name = "task_id") private UUID taskId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 32) private ProjectActivityType eventType;
    @Column(nullable = false, length = 240) private String summary;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    protected ProjectActivity() { }

    public static ProjectActivity create(UUID ownerId, UUID projectId, UUID taskId,
            ProjectActivityType eventType, String summary, Instant occurredAt) {
        ProjectActivity event = new ProjectActivity();
        event.id = UUID.randomUUID(); event.ownerId = ownerId; event.projectId = projectId;
        event.actorId = ownerId; event.taskId = taskId; event.eventType = eventType;
        event.summary = summary; event.occurredAt = occurredAt;
        return event;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getProjectId() { return projectId; }
    public UUID getActorId() { return actorId; }
    public UUID getTaskId() { return taskId; }
    public ProjectActivityType getEventType() { return eventType; }
    public String getSummary() { return summary; }
    public Instant getOccurredAt() { return occurredAt; }
}
