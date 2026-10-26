package com.taskos.time.entity;

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
@Table(name = "reminders", schema = "task_os")
public class Reminder {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "task_id") private UUID taskId;
    @Column(name = "time_block_id") private UUID timeBlockId;
    @Column(name = "goal_id") private UUID goalId;
    @Column(name = "habit_id") private UUID habitId;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "scheduled_for", nullable = false) private Instant scheduledFor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ReminderStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "read_at") private Instant readAt;
    @Column(name = "dismissed_at") private Instant dismissedAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version private Long version;

    protected Reminder() { }
    public static Reminder create(UUID ownerId, UUID taskId, UUID timeBlockId, String title, Instant scheduledFor, Instant now) {
        Reminder item = new Reminder(); item.id = UUID.randomUUID(); item.ownerId = ownerId; item.taskId = taskId;
        item.timeBlockId = timeBlockId; item.title = title; item.scheduledFor = scheduledFor;
        item.status = ReminderStatus.PENDING; item.createdAt = now; return item;
    }
    public static Reminder forGoal(UUID ownerId, UUID goalId, String title, Instant scheduledFor, Instant now) {
        Reminder item = create(ownerId, null, null, title, scheduledFor, now); item.goalId = goalId; return item;
    }
    public static Reminder forHabit(UUID ownerId, UUID habitId, String title, Instant scheduledFor, Instant now) {
        Reminder item = create(ownerId, null, null, title, scheduledFor, now); item.habitId = habitId; return item;
    }
    public void deliver(Instant now) { if (status != ReminderStatus.PENDING) return; status = ReminderStatus.DELIVERED; deliveredAt = now; attemptCount++; }
    public void snooze(Instant next) { if (status != ReminderStatus.PENDING && status != ReminderStatus.DELIVERED) throw new IllegalStateException("This reminder cannot be snoozed."); status = ReminderStatus.PENDING; scheduledFor = next; deliveredAt = null; readAt = null; }
    public void dismiss(Instant now) { if (status == ReminderStatus.DISMISSED || status == ReminderStatus.CANCELLED) throw new IllegalStateException("This reminder has already been dismissed."); status = ReminderStatus.DISMISSED; dismissedAt = now; }
    public void markRead(Instant now) { if (readAt == null) readAt = now; }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getTaskId() { return taskId; }
    public UUID getTimeBlockId() { return timeBlockId; }
    public UUID getGoalId() { return goalId; }
    public UUID getHabitId() { return habitId; }
    public String getTitle() { return title; }
    public Instant getScheduledFor() { return scheduledFor; }
    public ReminderStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
    public Instant getDismissedAt() { return dismissedAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
}
