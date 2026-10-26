package com.taskos.time.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "in_app_notifications", schema = "task_os")
public class InAppNotification {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "reminder_id", nullable = false, unique = true, updatable = false) private UUID reminderId;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "task_id") private UUID taskId;
    @Column(name = "delivered_at", nullable = false) private Instant deliveredAt;
    @Column(name = "read_at") private Instant readAt;
    protected InAppNotification() { }
    public static InAppNotification deliver(UUID ownerId, UUID reminderId, String title, UUID taskId, Instant now) {
        InAppNotification item = new InAppNotification(); item.id = UUID.randomUUID(); item.ownerId = ownerId;
        item.reminderId = reminderId; item.title = title; item.taskId = taskId; item.deliveredAt = now; return item;
    }
    public void markRead(Instant now) { if (readAt == null) readAt = now; }
    public void redeliver(Instant now) { deliveredAt = now; readAt = null; }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getReminderId() { return reminderId; }
    public String getTitle() { return title; }
    public UUID getTaskId() { return taskId; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
}
