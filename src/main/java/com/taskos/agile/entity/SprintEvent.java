package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sprint_events", schema = "task_os")
public class SprintEvent {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(name = "sprint_id", updatable = false) private UUID sprintId;
    @Column(name = "task_id", updatable = false) private UUID taskId;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 32) private SprintEventType eventType;
    @Column(nullable = false, length = 240) private String summary;
    @Column(name = "previous_value", length = 500) private String previousValue;
    @Column(name = "new_value", length = 500) private String newValue;
    @Column(length = 500) private String reason;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    protected SprintEvent() { }
    public static SprintEvent create(UUID projectId, UUID sprintId, UUID taskId, UUID actorId,
            SprintEventType type, String summary, String previous, String next, String reason, Instant now) {
        SprintEvent event = new SprintEvent(); event.id = UUID.randomUUID(); event.projectId = projectId;
        event.sprintId = sprintId; event.taskId = taskId; event.actorId = actorId; event.eventType = type;
        event.summary = summary; event.previousValue = previous; event.newValue = next; event.reason = reason;
        event.occurredAt = now; return event;
    }
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getSprintId() { return sprintId; }
    public UUID getTaskId() { return taskId; }
    public SprintEventType getEventType() { return eventType; }
    public String getSummary() { return summary; }
    public String getPreviousValue() { return previousValue; }
    public String getNewValue() { return newValue; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
