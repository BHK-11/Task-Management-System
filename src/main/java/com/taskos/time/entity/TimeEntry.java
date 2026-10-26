package com.taskos.time.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "time_entries", schema = "task_os")
public class TimeEntry {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "task_id") private UUID taskId;
    @Column(name = "focus_session_id", unique = true) private UUID focusSessionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TimeEntrySource source;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TimeEntryStatus status;
    @Column(nullable = false, length = 240) private String description;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "resumed_at") private Instant resumedAt;
    @Column(name = "accumulated_seconds", nullable = false) private long accumulatedSeconds;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected TimeEntry() { }
    public static TimeEntry start(UUID ownerId, UUID taskId, String description, Instant now) {
        TimeEntry entry = new TimeEntry(); entry.id = UUID.randomUUID(); entry.ownerId = ownerId; entry.taskId = taskId;
        entry.source = TimeEntrySource.TIMER; entry.status = TimeEntryStatus.RUNNING;
        entry.description = description == null ? "" : description; entry.startedAt = now; entry.resumedAt = now;
        entry.createdAt = now; entry.updatedAt = now; return entry;
    }
    public static TimeEntry manual(UUID ownerId, UUID taskId, String description, Instant start, Instant end, Instant now) {
        TimeEntry entry = new TimeEntry(); entry.id = UUID.randomUUID(); entry.ownerId = ownerId; entry.taskId = taskId;
        entry.source = TimeEntrySource.MANUAL; entry.status = TimeEntryStatus.COMPLETED;
        entry.description = description == null ? "" : description; entry.startedAt = start; entry.endedAt = end;
        entry.accumulatedSeconds = Duration.between(start, end).toSeconds(); entry.createdAt = now; entry.updatedAt = now;
        return entry;
    }
    public static TimeEntry fromFocus(UUID ownerId, UUID taskId, UUID focusSessionId, String description, Instant start, Instant end, long seconds, Instant now) {
        TimeEntry entry = new TimeEntry(); entry.id = UUID.randomUUID(); entry.ownerId = ownerId; entry.taskId = taskId;
        entry.focusSessionId = focusSessionId; entry.source = TimeEntrySource.FOCUS_SESSION; entry.status = TimeEntryStatus.COMPLETED;
        entry.description = description == null ? "Focus session" : description; entry.startedAt = start; entry.endedAt = end;
        entry.accumulatedSeconds = seconds; entry.createdAt = now; entry.updatedAt = now; return entry;
    }
    public void pause(long seconds, Instant now) { require(TimeEntryStatus.RUNNING); accumulatedSeconds += Math.max(0, seconds); resumedAt = null; status = TimeEntryStatus.PAUSED; updatedAt = now; }
    public void resume(Instant now) { require(TimeEntryStatus.PAUSED); resumedAt = now; status = TimeEntryStatus.RUNNING; updatedAt = now; }
    public void stop(long seconds, Instant now) { if (status == TimeEntryStatus.COMPLETED) throw new IllegalStateException("This time entry has already ended."); accumulatedSeconds += Math.max(0, seconds); resumedAt = null; endedAt = now; status = TimeEntryStatus.COMPLETED; updatedAt = now; }
    public void editManual(UUID taskId, String description, Instant start, Instant end, Instant now) {
        if (source != TimeEntrySource.MANUAL || status != TimeEntryStatus.COMPLETED) throw new IllegalStateException("Only completed manual entries can be edited.");
        this.taskId = taskId; this.description = description == null ? "" : description;
        this.startedAt = start; this.endedAt = end; this.accumulatedSeconds = Duration.between(start, end).toSeconds(); this.updatedAt = now;
    }
    private void require(TimeEntryStatus expected) { if (status != expected) throw new IllegalStateException("That time tracking action is not available in the current state."); }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getTaskId() { return taskId; }
    public UUID getFocusSessionId() { return focusSessionId; }
    public TimeEntrySource getSource() { return source; }
    public TimeEntryStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getResumedAt() { return resumedAt; }
    public long getAccumulatedSeconds() { return accumulatedSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
