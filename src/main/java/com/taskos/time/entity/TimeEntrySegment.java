package com.taskos.time.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "time_entry_segments", schema = "task_os")
public class TimeEntrySegment {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "entry_id", nullable = false, updatable = false) private UUID entryId;
    @Column(name = "focus_interval_id", unique = true) private UUID focusIntervalId;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    protected TimeEntrySegment() { }
    public static TimeEntrySegment start(UUID ownerId, UUID entryId, Instant now) {
        TimeEntrySegment segment = new TimeEntrySegment(); segment.id = UUID.randomUUID(); segment.ownerId = ownerId;
        segment.entryId = entryId; segment.startedAt = now; return segment;
    }
    public static TimeEntrySegment focus(UUID ownerId, UUID entryId, UUID intervalId, Instant start, Instant end) {
        TimeEntrySegment segment = new TimeEntrySegment(); segment.id = UUID.randomUUID(); segment.ownerId = ownerId;
        segment.entryId = entryId; segment.focusIntervalId = intervalId; segment.startedAt = start; segment.endedAt = end; return segment;
    }
    public Instant close(Instant now) {
        if (endedAt != null) throw new IllegalStateException("This segment has already ended.");
        // PostgreSQL timestamps are stored to microsecond precision.
        endedAt = now.truncatedTo(ChronoUnit.MICROS);
        Instant persistedStart = startedAt.truncatedTo(ChronoUnit.MICROS);
        if (!endedAt.isAfter(persistedStart)) endedAt = persistedStart.plus(1, ChronoUnit.MICROS);
        return endedAt;
    }
    public void editManual(Instant start, Instant end) { if (focusIntervalId != null) throw new IllegalStateException("Focus time is read-only."); if (!end.isAfter(start)) throw new IllegalArgumentException("A tracked segment must have positive duration."); startedAt = start; endedAt = end; }
    public long seconds(Instant now) { return Duration.between(startedAt, endedAt == null ? now : endedAt).toSeconds(); }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getEntryId() { return entryId; }
    public UUID getFocusIntervalId() { return focusIntervalId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
}
