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
@Table(name = "focus_intervals", schema = "task_os")
public class FocusInterval {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "session_id", nullable = false, updatable = false) private UUID sessionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private FocusPhase phase;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private FocusIntervalState state;
    @Column(name = "planned_seconds", nullable = false) private int plannedSeconds;
    @Column(name = "accumulated_seconds", nullable = false) private long accumulatedSeconds;
    @Column(name = "resumed_at") private Instant resumedAt;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version private Long version;

    protected FocusInterval() { }
    public static FocusInterval start(UUID ownerId, UUID sessionId, FocusPhase phase, int plannedSeconds, Instant now) {
        FocusInterval interval = new FocusInterval(); interval.id = UUID.randomUUID(); interval.ownerId = ownerId;
        interval.sessionId = sessionId; interval.phase = phase; interval.state = FocusIntervalState.RUNNING;
        interval.plannedSeconds = plannedSeconds; interval.startedAt = now; interval.resumedAt = now; return interval;
    }
    public long liveSeconds(Instant now) { return Math.min(plannedSeconds, accumulatedSeconds + (resumedAt == null ? 0 : Math.max(0, Duration.between(resumedAt, now).toSeconds()))); }
    public void pause(Instant now) { if (state != FocusIntervalState.RUNNING) throw new IllegalStateException("This interval is not running."); accumulatedSeconds = liveSeconds(now); resumedAt = null; state = FocusIntervalState.PAUSED; completedAt = now; }
    public long complete(Instant now, boolean skipped) { long elapsed = liveSeconds(now); accumulatedSeconds = skipped ? elapsed : plannedSeconds; resumedAt = null; state = skipped ? FocusIntervalState.SKIPPED : FocusIntervalState.COMPLETED; completedAt = now; return accumulatedSeconds; }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getSessionId() { return sessionId; }
    public FocusPhase getPhase() { return phase; }
    public FocusIntervalState getState() { return state; }
    public int getPlannedSeconds() { return plannedSeconds; }
    public long getAccumulatedSeconds() { return accumulatedSeconds; }
    public Instant getResumedAt() { return resumedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
