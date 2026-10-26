package com.taskos.time.service;

import com.taskos.task.repository.TaskRepository;
import com.taskos.time.dto.FocusTimerView;
import com.taskos.time.dto.FocusStartForm;
import com.taskos.time.entity.FocusInterval;
import com.taskos.time.entity.FocusIntervalState;
import com.taskos.time.entity.FocusMode;
import com.taskos.time.entity.FocusPhase;
import com.taskos.time.entity.FocusSession;
import com.taskos.time.entity.FocusState;
import com.taskos.time.entity.TimeEntry;
import com.taskos.time.entity.TimeEntrySegment;
import com.taskos.time.repository.FocusIntervalRepository;
import com.taskos.time.repository.FocusSessionRepository;
import com.taskos.time.repository.TimeEntryRepository;
import com.taskos.time.repository.TimeEntrySegmentRepository;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FocusSessionService {
    private static final List<FocusState> ACTIVE = List.of(FocusState.RUNNING, FocusState.PAUSED);
    private final FocusSessionRepository sessions;
    private final FocusIntervalRepository intervals;
    private final TimeEntryRepository entries;
    private final TimeEntrySegmentRepository segments;
    private final TaskRepository tasks;
    private final UserAccountRepository users;
    private final Clock clock;

    public FocusSessionService(FocusSessionRepository sessions, FocusIntervalRepository intervals,
            TimeEntryRepository entries, TimeEntrySegmentRepository segments, TaskRepository tasks,
            UserAccountRepository users, Clock clock) {
        this.sessions = sessions; this.intervals = intervals; this.entries = entries; this.segments = segments;
        this.tasks = tasks; this.users = users; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FocusSession active(UUID ownerId) { return sessions.findFirstByOwnerIdAndStateInOrderByStartedAtDesc(ownerId, ACTIVE).orElse(null); }
    @Transactional(readOnly = true)
    public FocusTimerView timer(UUID ownerId) {
        FocusSession session = active(ownerId);
        if (session == null) return null;
        List<FocusInterval> history = intervals.findAllByOwnerIdAndSessionIdOrderByStartedAtAsc(ownerId, session.getId());
        long used = session.getAccumulatedPhaseSeconds();
        FocusInterval current = history.stream().filter(i -> i.getState() == FocusIntervalState.RUNNING).reduce((a, b) -> b).orElse(null);
        if (current != null && session.getState() == FocusState.RUNNING) used += current.liveSeconds(clock.instant());
        int planned = phaseDuration(session, session.getPhase());
        return new FocusTimerView(session, Math.max(0, planned - used), history);
    }
    @Transactional(readOnly = true)
    public List<FocusSession> history(UUID ownerId) { return sessions.findTop50ByOwnerIdOrderByStartedAtDesc(ownerId); }
    @Transactional(readOnly = true)
    public List<FocusInterval> intervals(UUID ownerId, UUID sessionId) {
        ownSession(ownerId, sessionId);
        return intervals.findAllByOwnerIdAndSessionIdOrderByStartedAtAsc(ownerId, sessionId);
    }

    @Transactional
    public UUID start(UUID ownerId, FocusStartForm form) {
        var user = users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        if (active(ownerId) != null) throw new TimeRuleException("Finish or stop the active focus session before starting another.");
        if (form.getMode() == null || form.getFocusMinutes() < 1 || form.getFocusMinutes() > 240
                || form.getShortBreakMinutes() < 1 || form.getShortBreakMinutes() > 60
                || form.getLongBreakMinutes() < 1 || form.getLongBreakMinutes() > 120)
            throw new TimeRuleException("Choose focus and break lengths within the displayed limits.");
        validateTask(ownerId, form.getTaskId());
        Instant now = clock.instant();
        FocusSession session = sessions.save(FocusSession.start(user.getId(), form.getTaskId(), form.getMode(),
                form.getFocusMinutes(), form.getShortBreakMinutes(), form.getLongBreakMinutes(), form.getNotes(), now));
        intervals.save(FocusInterval.start(ownerId, session.getId(), FocusPhase.FOCUS, form.getFocusMinutes() * 60, now));
        return session.getId();
    }

    @Transactional
    public void pause(UUID ownerId, UUID id) {
        FocusSession session = lockedSession(ownerId, id);
        if (session.getState() != FocusState.RUNNING) throw new TimeRuleException("Only a running focus session can be paused.");
        Instant now = clock.instant(); FocusInterval interval = currentInterval(ownerId, id);
        long elapsed = interval.liveSeconds(now); interval.pause(now); session.pause(elapsed);
    }

    @Transactional
    public void resume(UUID ownerId, UUID id) {
        FocusSession session = lockedSession(ownerId, id);
        if (session.getState() != FocusState.PAUSED) throw new TimeRuleException("Only a paused focus session can be resumed.");
        Instant now = clock.instant(); session.resume(now);
        int remaining = Math.max(1, phaseDuration(session, session.getPhase()) - Math.toIntExact(session.getAccumulatedPhaseSeconds()));
        intervals.save(FocusInterval.start(ownerId, id, session.getPhase(), remaining, now));
    }

    @Transactional
    public void completePhase(UUID ownerId, UUID id) {
        FocusSession session = lockedSession(ownerId, id);
        if (session.getState() != FocusState.RUNNING) throw new TimeRuleException("The current focus interval is not running.");
        Instant now = clock.instant(); FocusInterval interval = currentInterval(ownerId, id);
        if (interval.liveSeconds(now) < interval.getPlannedSeconds()) throw new TimeRuleException("The timer has not reached zero yet.");
        interval.complete(now, false);
        if (session.getPhase() == FocusPhase.FOCUS) {
            if (session.getMode() == FocusMode.DEEP_WORK) {
                session.transition(FocusPhase.FOCUS, phaseDuration(session, FocusPhase.FOCUS), 0, now);
                session.finish(FocusState.COMPLETED, 0, now);
                saveFocusEntry(session, now);
                return;
            }
            FocusPhase next = (session.getCompletedCycles() + 1) % 4 == 0 ? FocusPhase.LONG_BREAK : FocusPhase.SHORT_BREAK;
            session.transition(next, phaseDuration(session, FocusPhase.FOCUS), 0, now);
            intervals.save(FocusInterval.start(ownerId, id, next, phaseDuration(session, next), now));
        } else {
            int actualBreak = Math.toIntExact(session.getAccumulatedPhaseSeconds() + interval.getPlannedSeconds());
            session.transition(FocusPhase.FOCUS, 0, actualBreak, now);
            intervals.save(FocusInterval.start(ownerId, id, FocusPhase.FOCUS, session.getFocusMinutes() * 60, now));
        }
    }

    @Transactional
    public void skipBreak(UUID ownerId, UUID id) {
        FocusSession session = lockedSession(ownerId, id);
        if (session.getState() != FocusState.RUNNING || session.getPhase() == FocusPhase.FOCUS)
            throw new TimeRuleException("Only a running break can be skipped.");
        Instant now = clock.instant(); FocusInterval interval = currentInterval(ownerId, id);
        long elapsed = interval.complete(now, true);
        session.transition(FocusPhase.FOCUS, 0,
                Math.toIntExact(session.getAccumulatedPhaseSeconds() + elapsed), now);
        intervals.save(FocusInterval.start(ownerId, id, FocusPhase.FOCUS, session.getFocusMinutes() * 60, now));
    }

    @Transactional
    public void stop(UUID ownerId, UUID id) {
        FocusSession session = lockedSession(ownerId, id);
        Instant now = clock.instant();
        if (session.getState() == FocusState.RUNNING) {
            FocusInterval interval = currentInterval(ownerId, id);
            long elapsed = interval.liveSeconds(now); interval.pause(now); session.pause(elapsed);
        }
        session.finish(FocusState.CANCELLED, 0, now);
        saveFocusEntry(session, now);
    }

    private void saveFocusEntry(FocusSession session, Instant now) {
        if (session.getFocusSeconds() <= 0 || entries.existsByFocusSessionId(session.getId())) return;
        String summary = session.getNotes().isBlank() ? "Focus session" : session.getNotes();
        TimeEntry entry = entries.save(TimeEntry.fromFocus(session.getOwnerId(), session.getTaskId(), session.getId(),
                summary, session.getStartedAt(), now, session.getFocusSeconds(), now));
        for (FocusInterval interval : intervals.findAllByOwnerIdAndSessionIdOrderByStartedAtAsc(session.getOwnerId(), session.getId())) {
            if (interval.getPhase() == FocusPhase.FOCUS && interval.getAccumulatedSeconds() > 0 && interval.getCompletedAt() != null)
                segments.save(TimeEntrySegment.focus(session.getOwnerId(), entry.getId(), interval.getId(), interval.getStartedAt(), interval.getCompletedAt()));
        }
    }
    private int phaseDuration(FocusSession session, FocusPhase phase) {
        return switch (phase) {
            case FOCUS -> session.getFocusMinutes() * 60;
            case SHORT_BREAK -> session.getShortBreakMinutes() * 60;
            case LONG_BREAK -> session.getLongBreakMinutes() * 60;
        };
    }
    private FocusInterval currentInterval(UUID ownerId, UUID sessionId) {
        return intervals.findFirstByOwnerIdAndSessionIdAndStateOrderByStartedAtDesc(ownerId, sessionId, FocusIntervalState.RUNNING)
                .orElseThrow(() -> new TimeRuleException("The active focus interval is missing."));
    }
    private FocusSession lockedSession(UUID ownerId, UUID id) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        return sessions.findLockedByIdAndOwner(id, ownerId).orElseThrow(() -> new TimeRuleException("That focus session is not available."));
    }
    private FocusSession ownSession(UUID ownerId, UUID id) { return sessions.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new TimeRuleException("That focus session is not available.")); }
    private void validateTask(UUID ownerId, UUID taskId) {
        if (taskId != null && tasks.findDetailByIdAndOwner(taskId, ownerId).isEmpty()) throw new TimeRuleException("Choose one of your own tasks.");
    }
}
