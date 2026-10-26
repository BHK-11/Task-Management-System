package com.taskos.time.service;

import com.taskos.task.repository.TaskRepository;
import com.taskos.time.dto.TimeBlockForm;
import com.taskos.time.entity.TimeBlock;
import com.taskos.time.entity.TimeBlockStatus;
import com.taskos.time.repository.TimeBlockRepository;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeBlockService {
    private static final Duration MAX_BLOCK = Duration.ofHours(16);
    private final TimeBlockRepository blocks;
    private final TaskRepository tasks;
    private final UserAccountRepository users;
    private final TimeZoneResolver zones;
    private final java.time.Clock clock;

    public TimeBlockService(TimeBlockRepository blocks, TaskRepository tasks, UserAccountRepository users,
            TimeZoneResolver zones, java.time.Clock clock) {
        this.blocks = blocks; this.tasks = tasks; this.users = users; this.zones = zones; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TimeBlock> range(UUID ownerId, Instant start, Instant end) {
        return blocks.findAllByOwnerIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(ownerId, end, start);
    }

    @Transactional(readOnly = true)
    public List<TimeBlock> all(UUID ownerId) { return blocks.findAllByOwnerIdAndStatusOrderByStartAtAsc(ownerId, TimeBlockStatus.PLANNED); }

    @Transactional(readOnly = true)
    public TimeBlock get(UUID ownerId, UUID id) { return block(ownerId, id); }

    @Transactional
    public BlockMutation create(UUID ownerId, TimeBlockForm form) {
        var user = users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        UUID taskId = validateTask(ownerId, form.getTaskId());
        ZoneId zone = zones.zone(ownerId);
        Instant start = resolve(form.getStartAt(), zone), end = resolve(form.getEndAt(), zone);
        validateInterval(start, end);
        Instant now = clock.instant();
        boolean conflict = blocks.existsOverlap(ownerId, start, end, null);
        TimeBlock block = blocks.save(TimeBlock.create(user.getId(), taskId, cleanTitle(form.getTitle()), start,
                end, zone.getId(), cleanNotes(form.getNotes()), now));
        return new BlockMutation(block.getId(), conflict);
    }

    @Transactional
    public boolean update(UUID ownerId, UUID id, TimeBlockForm form) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        TimeBlock block = blocks.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new TimeRuleException("That time block is not available."));
        if (block.getVersion() != form.getVersion()) throw new TimeConflictException("This time block changed in another window. Reload it before saving.");
        UUID taskId = validateTask(ownerId, form.getTaskId());
        ZoneId zone = zones.zone(ownerId);
        Instant start = resolve(form.getStartAt(), zone), end = resolve(form.getEndAt(), zone);
        validateInterval(start, end);
        boolean conflict = blocks.existsOverlap(ownerId, start, end, id);
        block.update(taskId, cleanTitle(form.getTitle()), start, end, zone.getId(), cleanNotes(form.getNotes()), clock.instant());
        return conflict;
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        TimeBlock block = blocks.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new TimeRuleException("That time block is not available."));
        blocks.delete(block);
    }

    public static Instant resolve(LocalDateTime local, ZoneId zone) {
        if (local == null) throw new TimeRuleException("Choose both a start and end time.");
        return LocalTimePolicy.resolve(local, zone).toInstant();
    }

    private UUID validateTask(UUID ownerId, UUID taskId) {
        if (taskId != null && tasks.findDetailByIdAndOwner(taskId, ownerId).isEmpty())
            throw new TimeRuleException("Choose one of your own tasks.");
        return taskId;
    }
    private static void validateInterval(Instant start, Instant end) {
        if (!end.isAfter(start)) throw new TimeRuleException("The end time must be after the start time.");
        if (Duration.between(start, end).compareTo(MAX_BLOCK) > 0) throw new TimeRuleException("A time block cannot exceed 16 hours.");
    }
    private static String cleanTitle(String title) {
        if (title == null || title.isBlank()) throw new TimeRuleException("Enter a title for the time block.");
        String clean = title.strip(); if (clean.length() > 160) throw new TimeRuleException("Use 160 characters or fewer for the title."); return clean;
    }
    private static String cleanNotes(String notes) { return notes == null ? "" : notes.strip(); }
    private TimeBlock block(UUID ownerId, UUID id) { return blocks.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new TimeRuleException("That time block is not available.")); }
}
