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
@Table(name = "focus_sessions", schema = "task_os")
public class FocusSession {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "task_id") private UUID taskId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private FocusMode mode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private FocusState state;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private FocusPhase phase;
    @Column(name = "focus_minutes", nullable = false) private int focusMinutes;
    @Column(name = "short_break_minutes", nullable = false) private int shortBreakMinutes;
    @Column(name = "long_break_minutes", nullable = false) private int longBreakMinutes;
    @Column(name = "completed_cycles", nullable = false) private int completedCycles;
    @Column(name = "accumulated_phase_seconds", nullable = false) private long accumulatedPhaseSeconds;
    @Column(name = "focus_seconds", nullable = false) private long focusSeconds;
    @Column(name = "break_seconds", nullable = false) private long breakSeconds;
    @Column(name = "resumed_at") private Instant resumedAt;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(nullable = false, length = 1200) private String notes = "";
    @Version private Long version;

    protected FocusSession() { }

    public static FocusSession start(UUID ownerId, UUID taskId, FocusMode mode, int focusMinutes,
            int shortBreakMinutes, int longBreakMinutes, String notes, Instant now) {
        FocusSession session = new FocusSession();
        session.id = UUID.randomUUID(); session.ownerId = ownerId; session.taskId = taskId;
        session.mode = mode; session.state = FocusState.RUNNING; session.phase = FocusPhase.FOCUS;
        session.focusMinutes = focusMinutes; session.shortBreakMinutes = shortBreakMinutes;
        session.longBreakMinutes = longBreakMinutes; session.startedAt = now; session.resumedAt = now;
        session.notes = notes == null ? "" : notes;
        return session;
    }

    public void pause(long elapsedSeconds) {
        require(FocusState.RUNNING); accumulatedPhaseSeconds += Math.max(0, elapsedSeconds);
        resumedAt = null; state = FocusState.PAUSED;
    }
    public void resume(Instant now) { require(FocusState.PAUSED); resumedAt = now; state = FocusState.RUNNING; }
    public void transition(FocusPhase next, int completedFocusSeconds, int completedBreakSeconds, Instant now) {
        require(FocusState.RUNNING);
        focusSeconds += Math.max(0, completedFocusSeconds); breakSeconds += Math.max(0, completedBreakSeconds);
        if (phase == FocusPhase.FOCUS) completedCycles++;
        phase = next; accumulatedPhaseSeconds = 0; resumedAt = now;
    }
    public void finish(FocusState finalState, long partialSeconds, Instant now) {
        if (state != FocusState.RUNNING && state != FocusState.PAUSED) throw new IllegalStateException("This focus session has already ended.");
        long active = accumulatedPhaseSeconds + Math.max(0, partialSeconds);
        if (phase == FocusPhase.FOCUS) focusSeconds += active; else breakSeconds += active;
        state = finalState; resumedAt = null; completedAt = now;
    }
    private void require(FocusState expected) { if (state != expected) throw new IllegalStateException("That focus action is not available in the current state."); }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getTaskId() { return taskId; }
    public FocusMode getMode() { return mode; }
    public FocusState getState() { return state; }
    public FocusPhase getPhase() { return phase; }
    public int getFocusMinutes() { return focusMinutes; }
    public int getShortBreakMinutes() { return shortBreakMinutes; }
    public int getLongBreakMinutes() { return longBreakMinutes; }
    public int getCompletedCycles() { return completedCycles; }
    public long getAccumulatedPhaseSeconds() { return accumulatedPhaseSeconds; }
    public long getFocusSeconds() { return focusSeconds; }
    public long getBreakSeconds() { return breakSeconds; }
    public Instant getResumedAt() { return resumedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getNotes() { return notes; }
    public long getVersion() { return version == null ? 0 : version; }
}
