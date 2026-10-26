package com.taskos.task.entity;

import com.taskos.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_activity", schema = "task_os", indexes = {
        @Index(name = "ix_task_activity_task_time", columnList = "owner_id, task_id, occurred_at"),
        @Index(name = "ix_task_activity_owner_time", columnList = "owner_id, occurred_at")
})
public class TaskActivity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "task_id")
    private UUID taskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private UserAccount actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private TaskActivityType eventType;

    @Column(nullable = false, length = 240)
    private String summary;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected TaskActivity() {
    }

    public static TaskActivity create(UUID taskId, UserAccount owner, UserAccount actor,
            TaskActivityType eventType, String summary, Instant occurredAt) {
        TaskActivity event = new TaskActivity();
        event.id = UUID.randomUUID();
        event.taskId = taskId;
        event.owner = owner;
        event.actor = actor;
        event.eventType = eventType;
        event.summary = summary;
        event.occurredAt = occurredAt;
        return event;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public UserAccount getOwner() { return owner; }
    public UserAccount getActor() { return actor; }
    public TaskActivityType getEventType() { return eventType; }
    public String getSummary() { return summary; }
    public Instant getOccurredAt() { return occurredAt; }
}
