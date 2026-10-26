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
@Table(name = "workspace_notifications", schema = "task_os", indexes =
        @Index(name = "ix_workspace_notifications_inbox", columnList = "recipient_id, read_at, delivered_at"))
public class WorkspaceNotification {
    @Id private UUID id;
    @Column(name = "workspace_id", nullable = false, updatable = false) private UUID workspaceId;
    @Column(name = "recipient_id", nullable = false, updatable = false) private UUID recipientId;
    @Column(name = "task_id", updatable = false) private UUID taskId;
    @Enumerated(EnumType.STRING) @Column(name = "notification_type", nullable = false, length = 16, updatable = false)
    private WorkspaceNotificationType type;
    @Column(nullable = false, length = 160, updatable = false) private String title;
    @Column(name = "delivered_at", nullable = false, updatable = false) private Instant deliveredAt;
    @Column(name = "read_at") private Instant readAt;
    protected WorkspaceNotification() { }
    public static WorkspaceNotification deliver(UUID workspaceId, UUID recipientId, UUID taskId,
            WorkspaceNotificationType type, String title, Instant now) {
        WorkspaceNotification item = new WorkspaceNotification(); item.id = UUID.randomUUID(); item.workspaceId = workspaceId;
        item.recipientId = recipientId; item.taskId = taskId; item.type = type; item.title = title; item.deliveredAt = now; return item;
    }
    public void markRead(Instant now) { if (readAt == null) readAt = now; }
    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getRecipientId() { return recipientId; }
    public UUID getTaskId() { return taskId; }
    public WorkspaceNotificationType getType() { return type; }
    public String getTitle() { return title; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
}
