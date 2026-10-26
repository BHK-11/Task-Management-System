package com.taskos.time.service;

import com.taskos.task.repository.TaskRepository;
import com.taskos.time.dto.ManualTimeEntryForm;
import com.taskos.time.dto.TrackingStartForm;
import com.taskos.time.entity.TimeEntry;
import com.taskos.time.entity.TimeEntrySegment;
import com.taskos.time.entity.TimeEntrySource;
import com.taskos.time.entity.TimeEntryStatus;
import com.taskos.time.repository.TimeEntryRepository;
import com.taskos.time.repository.TimeEntrySegmentRepository;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeTrackingService {
    private final TimeEntryRepository entries;
    private final TimeEntrySegmentRepository segments;
    private final TaskRepository tasks;
    private final UserAccountRepository users;
    private final TimeZoneResolver zones;
    private final java.time.Clock clock;

    public TimeTrackingService(TimeEntryRepository entries, TimeEntrySegmentRepository segments, TaskRepository tasks,
            UserAccountRepository users, TimeZoneResolver zones, java.time.Clock clock) {
        this.entries = entries; this.segments = segments; this.tasks = tasks; this.users = users; this.zones = zones; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> history(UUID ownerId) { return entries.findTop100ByOwnerIdOrderByStartedAtDesc(ownerId); }
    @Transactional(readOnly = true)
    public TimeEntry active(UUID ownerId) { return entries.findFirstByOwnerIdAndStatusInOrderByCreatedAtDesc(ownerId,
            List.of(TimeEntryStatus.RUNNING, TimeEntryStatus.PAUSED)).orElse(null); }
    @Transactional(readOnly = true)
    public long elapsed(UUID ownerId, UUID entryId) {
        TimeEntry entry = ownEntry(ownerId, entryId);
        return segments.findAllByEntryIdOrderByStartedAtAsc(entryId).stream().mapToLong(segment -> segment.seconds(clock.instant())).sum();
    }

    @Transactional
    public UUID start(UUID ownerId, TrackingStartForm form) {
        var user = users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        if (active(ownerId) != null) throw new TimeRuleException("Pause or stop the active timer before starting another.");
        validateTask(ownerId, form.getTaskId());
        Instant now = clock.instant();
        TimeEntry entry = entries.save(TimeEntry.start(user.getId(), form.getTaskId(), cleanDescription(form.getDescription()), now));
        segments.save(TimeEntrySegment.start(ownerId, entry.getId(), now));
        return entry.getId();
    }

    @Transactional
    public void pause(UUID ownerId, UUID entryId) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        TimeEntry entry = ownEntry(ownerId, entryId);
        if (entry.getStatus() != TimeEntryStatus.RUNNING) throw new TimeRuleException("Only a running timer can be paused.");
        TimeEntrySegment segment = openSegment(entryId);
        Instant now = segment.close(clock.instant());
        entry.pause(segment.seconds(now), now);
    }

    @Transactional
    public void resume(UUID ownerId, UUID entryId) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        TimeEntry entry = ownEntry(ownerId, entryId);
        if (entry.getStatus() != TimeEntryStatus.PAUSED) throw new TimeRuleException("Only a paused timer can be resumed.");
        TimeEntry other = active(ownerId);
        if (other != null && !other.getId().equals(entryId)) throw new TimeRuleException("Another timer is active. Pause or stop it first.");
        Instant now = clock.instant(); entry.resume(now); segments.save(TimeEntrySegment.start(ownerId, entryId, now));
    }

    @Transactional
    public void stop(UUID ownerId, UUID entryId) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        TimeEntry entry = ownEntry(ownerId, entryId);
        Instant now = clock.instant(); long elapsed = 0;
        if (entry.getStatus() == TimeEntryStatus.RUNNING) {
            TimeEntrySegment segment = openSegment(entryId); now = segment.close(now); elapsed = segment.seconds(now);
        } else if (entry.getStatus() != TimeEntryStatus.PAUSED) throw new TimeRuleException("This timer has already ended.");
        entry.stop(elapsed, now);
    }

    @Transactional
    public ManualEntryMutation addManual(UUID ownerId, ManualTimeEntryForm form) {
        var user = users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        validateTask(ownerId, form.getTaskId()); ZoneId zone = zones.zone(ownerId);
        Instant start = TimeBlockService.resolve(form.getStartAt(), zone), end = TimeBlockService.resolve(form.getEndAt(), zone);
        validateManualRange(start, end);
        Instant now = clock.instant(); boolean overlap = hasOverlap(ownerId, start, end, null, now);
        TimeEntry entry = entries.save(TimeEntry.manual(user.getId(), form.getTaskId(), cleanDescription(form.getDescription()), start, end, now));
        segments.save(TimeEntrySegment.focus(ownerId, entry.getId(), null, start, end));
        return new ManualEntryMutation(entry.getId(), overlap);
    }

    @Transactional
    public boolean editManual(UUID ownerId, UUID entryId, ManualTimeEntryForm form) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        TimeEntry entry = ownEntry(ownerId, entryId);
        if (entry.getSource() != TimeEntrySource.MANUAL) throw new TimeRuleException("Only manual entries can be edited.");
        if (entry.getVersion() != form.getVersion()) throw new TimeConflictException("This entry changed in another window. Reload it before saving.");
        validateTask(ownerId, form.getTaskId()); ZoneId zone = zones.zone(ownerId);
        Instant start = TimeBlockService.resolve(form.getStartAt(), zone), end = TimeBlockService.resolve(form.getEndAt(), zone);
        validateManualRange(start, end); Instant now = clock.instant();
        boolean overlap = hasOverlap(ownerId, start, end, entryId, now);
        entry.editManual(form.getTaskId(), cleanDescription(form.getDescription()), start, end, now);
        List<TimeEntrySegment> existing = segments.findAllByEntryIdOrderByStartedAtAsc(entryId);
        if (existing.size() != 1) throw new TimeRuleException("This manual entry cannot be edited safely.");
        existing.getFirst().editManual(start, end);
        return overlap;
    }

    @Transactional
    public void deleteManual(UUID ownerId, UUID entryId) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        TimeEntry entry = ownEntry(ownerId, entryId);
        if (entry.getSource() != TimeEntrySource.MANUAL) throw new TimeRuleException("Only manual entries can be deleted.");
        entries.delete(entry);
    }

    @Transactional(readOnly = true)
    public long exclusiveSeconds(UUID ownerId, Instant start, Instant end) {
        Instant now = clock.instant();
        List<TimeEntrySegment> found = segments.findOverlapping(ownerId, start, end);
        List<Instant[]> intervals = new ArrayList<>();
        for (TimeEntrySegment segment : found) {
            Instant a = segment.getStartedAt().isAfter(start) ? segment.getStartedAt() : start;
            Instant rawEnd = segment.getEndedAt() == null ? now : segment.getEndedAt();
            Instant b = rawEnd.isBefore(end) ? rawEnd : end;
            if (b.isAfter(a)) intervals.add(new Instant[] { a, b });
        }
        intervals.sort(Comparator.comparing(range -> range[0]));
        long seconds = 0; Instant startAt = null, endAt = null;
        for (Instant[] range : intervals) {
            if (startAt == null) { startAt = range[0]; endAt = range[1]; }
            else if (!range[0].isAfter(endAt)) { if (range[1].isAfter(endAt)) endAt = range[1]; }
            else { seconds += Duration.between(startAt, endAt).toSeconds(); startAt = range[0]; endAt = range[1]; }
        }
        if (startAt != null) seconds += Duration.between(startAt, endAt).toSeconds();
        return seconds;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> taskSeconds(UUID ownerId, Instant start, Instant end) {
        List<TimeEntrySegment> found = segments.findOverlapping(ownerId, start, end);
        Instant now = clock.instant();
        Map<UUID, Long> secondsByEntry = found.stream().collect(Collectors.groupingBy(TimeEntrySegment::getEntryId,
                Collectors.summingLong(segment -> clippedSeconds(segment, start, end, now))));
        return entries.findAllById(secondsByEntry.keySet()).stream().filter(entry -> entry.getTaskId() != null)
                .collect(Collectors.groupingBy(TimeEntry::getTaskId,
                        Collectors.summingLong(entry -> secondsByEntry.getOrDefault(entry.getId(), 0L))));
    }

    private boolean hasOverlap(UUID ownerId, Instant start, Instant end, UUID excludeEntryId, Instant now) {
        return segments.findOverlapping(ownerId, start, end).stream().filter(segment -> excludeEntryId == null || !segment.getEntryId().equals(excludeEntryId))
                .anyMatch(segment -> {
                    Instant segmentEnd = segment.getEndedAt() == null ? now : segment.getEndedAt();
                    return segment.getStartedAt().isBefore(end) && segmentEnd.isAfter(start);
                });
    }
    private void validateTask(UUID ownerId, UUID taskId) {
        if (taskId != null && tasks.findDetailByIdAndOwner(taskId, ownerId).isEmpty()) throw new TimeRuleException("Choose one of your own tasks.");
    }
    private TimeEntry ownEntry(UUID ownerId, UUID entryId) { return entries.findByIdAndOwnerId(entryId, ownerId).orElseThrow(() -> new TimeRuleException("That time entry is not available.")); }
    private TimeEntrySegment openSegment(UUID entryId) {
        List<TimeEntrySegment> history = segments.findAllByEntryIdOrderByStartedAtAsc(entryId);
        if (history.isEmpty() || history.getLast().getEndedAt() != null) throw new TimeRuleException("The active timer segment is missing.");
        return history.getLast();
    }
    private static String cleanDescription(String value) { return value == null ? "" : value.strip(); }
    private static void validateManualRange(Instant start, Instant end) {
        if (!end.isAfter(start)) throw new TimeRuleException("The end time must be after the start time.");
        if (Duration.between(start, end).compareTo(Duration.ofHours(24)) > 0) throw new TimeRuleException("A manual entry cannot exceed 24 hours.");
    }
    private static long clippedSeconds(TimeEntrySegment segment, Instant start, Instant end, Instant now) {
        Instant from = segment.getStartedAt().isAfter(start) ? segment.getStartedAt() : start;
        Instant segmentEnd = segment.getEndedAt() == null ? now : segment.getEndedAt();
        Instant to = segmentEnd.isBefore(end) ? segmentEnd : end;
        return to.isAfter(from) ? Duration.between(from, to).toSeconds() : 0;
    }
}
