package com.taskos.time.service;

import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskService;
import com.taskos.time.dto.RecurrenceForm;
import com.taskos.time.entity.MonthEndPolicy;
import com.taskos.time.entity.OccurrenceStatus;
import com.taskos.time.entity.RecurrenceFrequency;
import com.taskos.time.entity.RecurrenceOccurrence;
import com.taskos.time.entity.RecurrenceRule;
import com.taskos.time.repository.RecurrenceOccurrenceRepository;
import com.taskos.time.repository.RecurrenceRuleRepository;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurrenceService {
    private static final int MAX_CATCH_UP_PER_RULE = 5;
    private final RecurrenceRuleRepository rules;
    private final RecurrenceOccurrenceRepository occurrences;
    private final TaskRepository tasks;
    private final TaskService taskService;
    private final UserAccountRepository users;
    private final TimeZoneResolver zones;
    private final RecurrenceCalculator calculator;
    private final Clock clock;

    public RecurrenceService(RecurrenceRuleRepository rules, RecurrenceOccurrenceRepository occurrences,
            TaskRepository tasks, TaskService taskService, UserAccountRepository users, TimeZoneResolver zones,
            RecurrenceCalculator calculator, Clock clock) {
        this.rules = rules; this.occurrences = occurrences; this.tasks = tasks; this.taskService = taskService;
        this.users = users; this.zones = zones; this.calculator = calculator; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RecurrenceRule> list(UUID ownerId) { return rules.findAllByOwnerIdOrderByCreatedAtDesc(ownerId); }
    @Transactional(readOnly = true)
    public List<RecurrenceOccurrence> recentOccurrences(UUID ownerId) { return occurrences.findTop100ByOwnerIdOrderByOccurrenceDateDesc(ownerId); }

    @Transactional
    public UUID create(UUID ownerId, RecurrenceForm form) {
        var owner = users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        Task template = tasks.findDetailByIdAndOwner(form.getSourceTaskId(), ownerId)
                .orElseThrow(() -> new TimeRuleException("Choose one of your own tasks as the recurrence template."));
        if (form.getIntervalCount() < 1 || form.getIntervalCount() > 365) throw new TimeRuleException("The repeat interval must be between 1 and 365.");
        if (form.getEndDate() != null && form.getEndDate().isBefore(form.getStartDate())) throw new TimeRuleException("The end date must be on or after the first date.");
        if (form.getOccurrenceLimit() != null && form.getOccurrenceLimit() < 1) throw new TimeRuleException("Choose at least one occurrence.");
        String weekdays = normalizeWeekdays(form.getFrequency(), form.getWeekdays());
        ZoneId zone = zones.zone(ownerId);
        RecurrenceRule rule = RecurrenceRule.create(ownerId, template.getId(), form.getFrequency(), form.getIntervalCount(),
                zone.getId(), form.getStartDate(), form.getLocalTime(), weekdays, form.getMonthEndPolicy(),
                form.getEndDate(), form.getOccurrenceLimit(), clock.instant());
        return rules.save(rule).getId();
    }

    @Transactional
    public int processDue() {
        Instant now = clock.instant();
        LocalDate latest = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1);
        List<RecurrenceRule> candidates = rules.findTop100ByPausedFalseAndEndedFalseAndNextOccurrenceDateLessThanEqualOrderByNextOccurrenceDate(latest);
        int generated = 0;
        for (RecurrenceRule candidate : candidates) generated += processRule(candidate.getOwnerId(), candidate.getId(), now);
        return generated;
    }

    @Transactional
    public int processRule(UUID ownerId, UUID ruleId, Instant now) {
        RecurrenceRule rule = locked(ownerId, ruleId);
        if (rule.isPaused() || rule.finished()) return 0;
        ZoneId zone;
        try { zone = ZoneId.of(rule.getTimezoneId()); } catch (DateTimeException ex) { zone = zones.zone(ownerId); }
        int generated = 0;
        for (int i = 0; i < MAX_CATCH_UP_PER_RULE && !rule.finished(); i++) {
            LocalDate date = rule.getNextOccurrenceDate();
            if (!isDue(rule, date, zone, now)) break;
            if (occurrences.findByRuleIdAndOccurrenceDate(ruleId, date).isEmpty()) {
                UUID taskId = createOccurrenceTask(ownerId, rule, date);
                occurrences.save(RecurrenceOccurrence.generated(ownerId, ruleId, taskId, date, now));
                generated++;
            }
            advance(rule, date, now);
        }
        return generated;
    }

    @Transactional
    public void skipNext(UUID ownerId, UUID ruleId) {
        RecurrenceRule rule = locked(ownerId, ruleId); ensureActive(rule);
        LocalDate current = rule.getNextOccurrenceDate(); Instant now = clock.instant();
        if (occurrences.findByRuleIdAndOccurrenceDate(ruleId, current).isEmpty())
            occurrences.save(RecurrenceOccurrence.skipped(ownerId, ruleId, current, now));
        advance(rule, current, now);
    }

    @Transactional
    public UUID rescheduleNext(UUID ownerId, UUID ruleId, LocalDate newDate) {
        RecurrenceRule rule = locked(ownerId, ruleId); ensureActive(rule);
        LocalDate current = rule.getNextOccurrenceDate(); Instant now = clock.instant();
        if (!newDate.isAfter(LocalDate.now(clock.withZone(zones.zone(ownerId))))) throw new TimeRuleException("Choose a future date for the replacement occurrence.");
        if (occurrences.findByRuleIdAndOccurrenceDate(ruleId, current).isPresent()) throw new TimeRuleException("That occurrence has already been generated.");
        UUID taskId = createOccurrenceTask(ownerId, rule, newDate);
        occurrences.save(RecurrenceOccurrence.rescheduled(ownerId, ruleId, taskId, current, now));
        advance(rule, current, now); return taskId;
    }

    @Transactional
    public void pause(UUID ownerId, UUID ruleId, boolean paused) { locked(ownerId, ruleId).setPaused(paused, clock.instant()); }
    @Transactional
    public void end(UUID ownerId, UUID ruleId) { locked(ownerId, ruleId).end(clock.instant()); }

    private UUID createOccurrenceTask(UUID ownerId, RecurrenceRule rule, LocalDate dueDate) {
        Task template = tasks.findDetailByIdAndOwner(rule.getSourceTaskId(), ownerId)
                .orElseThrow(() -> new TimeRuleException("The recurrence template task is unavailable."));
        TaskForm form = new TaskForm(); form.setTitle(template.getTitle()); form.setDescription(template.getDescription());
        form.setStatus(TaskStatus.TODO); form.setPriority(template.getPriority()); form.setDueDate(dueDate);
        return taskService.create(ownerId, form);
    }
    private void advance(RecurrenceRule rule, LocalDate current, Instant now) {
        LocalDate next = calculator.next(rule, current);
        if (next == null) { rule.end(now); return; }
        rule.advance(next, now);
        if (rule.finished()) rule.end(now);
    }
    private static boolean isDue(RecurrenceRule rule, LocalDate date, ZoneId zone, Instant now) {
        Instant scheduled = LocalTimePolicy.resolve(date.atTime(rule.getLocalTime()), zone).toInstant();
        return !scheduled.isAfter(now) && (rule.getEndDate() == null || !date.isAfter(rule.getEndDate()));
    }
    private static String normalizeWeekdays(RecurrenceFrequency frequency, String value) {
        if (frequency != RecurrenceFrequency.WEEKLY) return "";
        try {
            String result = Arrays.stream((value == null ? "" : value).split(","))
                    .filter(v -> !v.isBlank()).map(String::trim).map(Integer::parseInt).distinct().sorted()
                    .map(day -> { if (day < 1 || day > 7) throw new IllegalArgumentException(); return Integer.toString(day); })
                    .collect(Collectors.joining(","));
            if (result.isBlank()) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException ex) { throw new TimeRuleException("For weekly repeats, select one or more weekdays from 1 (Monday) to 7 (Sunday)."); }
    }
    private static void ensureActive(RecurrenceRule rule) { if (rule.finished()) throw new TimeRuleException("This recurrence series has ended."); }
    private RecurrenceRule locked(UUID ownerId, UUID id) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        return rules.findLockedByIdAndOwner(id, ownerId).orElseThrow(() -> new TimeRuleException("That recurrence rule is not available."));
    }
}
