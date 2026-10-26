package com.taskos.collaboration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_activity", schema = "task_os", indexes =
        @Index(name = "ix_workspace_activity_timeline", columnList = "workspace_id, occurred_at"))
public class WorkspaceActivity {
    @Id private UUID id;
    @Column(name = "workspace_id", nullable = false, updatable = false) private UUID workspaceId;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 32, updatable = false)
    private WorkspaceEventType eventType;
    @Column(nullable = false, length = 240, updatable = false) private String summary;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    protected WorkspaceActivity() { }
    public static WorkspaceActivity create(UUID workspaceId, UUID actorId, WorkspaceEventType type, String summary, Instant now) {
        WorkspaceActivity event = new WorkspaceActivity(); event.id = UUID.randomUUID(); event.workspaceId = workspaceId;
        event.actorId = actorId; event.eventType = type; event.summary = summary; event.occurredAt = now; return event;
    }
    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getActorId() { return actorId; }
    public WorkspaceEventType getEventType() { return eventType; }
    public String getSummary() { return summary; }
    public Instant getOccurredAt() { return occurredAt; }
}
