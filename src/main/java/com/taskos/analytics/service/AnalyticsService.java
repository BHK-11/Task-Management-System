package com.taskos.analytics.service;

import com.taskos.analytics.dto.AnalyticsBucket;
import com.taskos.analytics.dto.AnalyticsMetricDefinition;
import com.taskos.analytics.dto.AnalyticsOverview;
import com.taskos.analytics.dto.AnalyticsProjectOption;
import com.taskos.analytics.dto.AnalyticsTrendPoint;
import com.taskos.analytics.repository.AnalyticsTaskRepository;
import com.taskos.ai.repository.AiPlanRepository;
import com.taskos.automation.entity.AutomationExecutionStatus;
import com.taskos.automation.repository.AutomationExecutionRepository;
import com.taskos.goals.service.GoalService;
import com.taskos.habits.service.HabitService;
import com.taskos.project.entity.ProjectStatus;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskActivityType;
import com.taskos.task.entity.TaskStatus;
import com.taskos.time.service.TimeTrackingService;
import com.taskos.time.service.TimeZoneResolver;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private static final String METRIC_VERSION = "1.0";
    private static final int MAX_COMPLETIONS_FOR_TREND = 10_000;
    private static final List<TaskStatus> OPEN = List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.ON_HOLD);
    private static final List<AnalyticsMetricDefinition> DEFINITIONS = List.of(
            metric("TASK_CREATED", "Tasks created", "Retained personal top-level tasks created in this period.", "Task.createdAt", "Count unarchived personal top-level tasks created in [start, end).", "tasks", "Deleted and archived tasks are excluded."),
            metric("TASK_COMPLETED_CURRENT", "Tasks completed", "Tasks currently complete whose last completion timestamp is in this period.", "Task.completedAt, Task.status", "Count unarchived personal top-level tasks with status COMPLETED and completedAt in [start, end).", "tasks", "Reopened tasks are excluded because their current completedAt is cleared."),
            metric("TASK_COMPLETION_EVENTS", "Completion events", "Recorded completion events for retained personal top-level tasks.", "TaskActivity.TASK_COMPLETED", "Count TASK_COMPLETED events in [start, end); one task may contribute more than one event if reopened and completed again.", "events", "Deleted and archived tasks are excluded."),
            metric("TASK_OPEN_CURRENT", "Open tasks", "Current open personal top-level tasks.", "Task.status", "Count TODO, IN_PROGRESS and ON_HOLD tasks.", "tasks", "Current state; independent of selected dates."),
            metric("TASK_OVERDUE_CURRENT", "Overdue", "Open tasks due before today in your account timezone.", "Task.dueDate, Task.status", "Count open personal top-level tasks with dueDate < today.", "tasks", "Date-only due dates use the account's local calendar day."),
            metric("TASK_DUE_TODAY", "Due today", "Open tasks due today in your account timezone.", "Task.dueDate, Task.status", "Count open personal top-level tasks with dueDate = today.", "tasks", "Date-only due dates use the account's local calendar day."),
            metric("TASK_DUE_NEXT_7_DAYS", "Due next 7 days", "Open tasks due tomorrow through seven days from today.", "Task.dueDate, Task.status", "Count open personal top-level tasks with dueDate in [tomorrow, today + 7 days].", "tasks", "Current deadlines; independent of selected dates."),
            metric("TASK_WITHOUT_DUE_DATE", "No due date", "Open personal top-level tasks without a due date.", "Task.dueDate, Task.status", "Count open tasks whose dueDate is null.", "tasks", "Current state; independent of selected dates."),
            metric("TASK_STATUS_CURRENT", "Task status distribution", "Current canonical status for retained personal top-level tasks.", "Task.status", "Count unarchived personal top-level tasks grouped by canonical TaskStatus.", "tasks", "Includes cancelled tasks; excludes archived and child tasks."),
            metric("TASK_PRIORITY_OPEN", "Open tasks by priority", "Current priority distribution of open personal top-level tasks.", "Task.priority, Task.status", "Count open tasks grouped by canonical TaskPriority.", "tasks", "Priority is a user-set planning attribute, not a productivity score."),
            metric("ACTUAL_TRACKED_TIME", "Actual tracked time", "Time recorded by timer, manual entries, and completed focus sessions.", "TimeEntrySegment", "Union duration of the owner's tracked segments clipped to the selected local-date interval.", "seconds", "Running segments are counted through the current instant; planned blocks are excluded."),
            metric("ACTIVE_PROJECTS", "Active projects", "Current active projects in personal scope.", "Project.status", "Count the owner's unarchived personal projects in ACTIVE state.", "projects", "Independent of the selected project and dates."),
            metric("ACTIVE_GOALS", "Active goals", "Current active goals in personal scope.", "Goal.status", "Count active, unarchived goals owned by the user.", "goals", "Independent of selected dates."),
            metric("ACTIVE_HABITS", "Active habits", "Current active habits in personal scope.", "Habit.status", "Count active habits owned by the user.", "habits", "Independent of selected dates; no adherence is inferred."),
            metric("AI_PLANS", "AI plans", "Plans requested by the user in this period.", "AiPlan.createdAt", "Count AI plans owned by the user created in [start, end).", "plans", "A plan request is not counted as a completed AI action."),
            metric("AI_VERIFIED_TASKS", "Verified AI tasks", "Tasks recorded as created and verified by an approved AI plan.", "AiPlanStep.status, resultTaskId", "Count completed AI plan steps with a non-null resultTaskId, for plans created in [start, end).", "tasks", "Counts persisted successful task outcomes, not model suggestions."),
            metric("AUTOMATION_RUNS", "Automation executions", "Personal automation execution records created in this period.", "AutomationExecution", "Count personal-scope execution rows by owner scope id.", "executions", "Retries remain part of one execution row."),
            metric("AUTOMATION_SUCCEEDED", "Automation succeeded", "Personal execution rows currently in SUCCEEDED state.", "AutomationExecution.status", "Count selected-period executions whose current status is SUCCEEDED.", "executions", "A retried execution contributes one result."),
            metric("AUTOMATION_FAILED", "Automation failed", "Personal executions currently retryable-failed, permanently failed, or dead-lettered.", "AutomationExecution.status", "Count selected-period executions in FAILED_RETRYABLE, FAILED_PERMANENT or DEAD_LETTER.", "executions", "One row per execution; retry attempts do not inflate the count."),
            metric("AUTOMATION_RETRIED", "Automation retried", "Personal executions with at least one recorded retry attempt.", "AutomationExecution.attemptCount", "Count selected-period execution rows where attemptCount > 0.", "executions", "Counts executions with retries, not the number of attempts."));

    private final AnalyticsTaskRepository tasks;
    private final ProjectRepository projects;
    private final GoalService goals;
    private final HabitService habits;
    private final TimeTrackingService tracking;
    private final TimeZoneResolver zones;
    private final AiPlanRepository aiPlans;
    private final AutomationExecutionRepository automation;
    private final Clock clock;

    public AnalyticsService(AnalyticsTaskRepository tasks, ProjectRepository projects,
            GoalService goals, HabitService habits,
            TimeTrackingService tracking, TimeZoneResolver zones, AiPlanRepository aiPlans,
            AutomationExecutionRepository automation, Clock clock) {
        this.tasks = tasks;
        this.projects = projects;
        this.goals = goals;
        this.habits = habits;
        this.tracking = tracking;
        this.zones = zones;
        this.aiPlans = aiPlans;
        this.automation = automation;
        this.clock = clock;
    }

    public AnalyticsOverview overview(UUID ownerId, LocalDate requestedStart, LocalDate requestedEnd, UUID projectId) {
        ZoneId zone = zones.zone(ownerId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate end = requestedEnd == null ? today : requestedEnd;
        LocalDate start = requestedStart == null ? end.minusDays(29) : requestedStart;
        long days = ChronoUnit.DAYS.between(start, end);
        if (start.isAfter(end) || end.isAfter(today) || days > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a date range within the last 366 days.");
        }
        if (projectId != null && projects.findByIdAndOwner_Id(projectId, ownerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Instant from = start.atStartOfDay(zone).toInstant();
        Instant until = end.plusDays(1).atStartOfDay(zone).toInstant();

        Map<TaskStatus, Long> statuses = new EnumMap<>(TaskStatus.class);
        tasks.statusCounts(ownerId, projectId).forEach(row -> statuses.put((TaskStatus) row[0], number(row[1])));
        List<AnalyticsBucket> statusBuckets = new ArrayList<>();
        for (TaskStatus status : TaskStatus.values()) {
            statusBuckets.add(new AnalyticsBucket(label(status.name()), statuses.getOrDefault(status, 0L), 0,
                    "/tasks?status=" + status.name()));
        }
        Map<TaskPriority, Long> priorities = new EnumMap<>(TaskPriority.class);
        tasks.openPriorityCounts(ownerId, projectId, OPEN).forEach(row -> priorities.put((TaskPriority) row[0], number(row[1])));
        List<AnalyticsBucket> priorityBuckets = new ArrayList<>();
        for (TaskPriority priority : TaskPriority.values()) {
            priorityBuckets.add(new AnalyticsBucket(label(priority.name()), priorities.getOrDefault(priority, 0L), 0,
                    "/tasks?priority=" + priority.name()));
        }
        statusBuckets = withPercentages(statusBuckets);
        priorityBuckets = withPercentages(priorityBuckets);

        long completed = tasks.completedBetween(ownerId, projectId, TaskStatus.COMPLETED, from, until);
        long completionEvents = tasks.completionEventCount(ownerId, projectId, TaskActivityType.TASK_COMPLETED, from, until);
        List<Instant> completionSamples = completionEvents <= MAX_COMPLETIONS_FOR_TREND
                ? tasks.completionEventSamples(ownerId, projectId, TaskActivityType.TASK_COMPLETED, from, until,
                        PageRequest.of(0, MAX_COMPLETIONS_FOR_TREND)) : List.of();
        List<AnalyticsTrendPoint> trend = completionEvents > MAX_COMPLETIONS_FOR_TREND
                ? List.of() : trend(start, end, zone, completionSamples);

        long open = OPEN.stream().mapToLong(status -> statuses.getOrDefault(status, 0L)).sum();
        long created = tasks.createdBetween(ownerId, projectId, from, until);
        long overdue = tasks.overdueCount(ownerId, projectId, OPEN, today);
        long dueToday = tasks.dueOn(ownerId, projectId, OPEN, today);
        long dueNextSevenDays = tasks.dueBetween(ownerId, projectId, OPEN, today.plusDays(1), today.plusDays(7));
        long noDueDate = tasks.unscheduledCount(ownerId, projectId, OPEN);
        Long actualWorkSeconds = projectId == null ? tracking.exclusiveSeconds(ownerId, from, until) : null;

        long aiPlanCount = aiPlans.countByOwner_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ownerId, from, until);
        long verifiedAiTasks = aiPlans.countVerifiedTasks(ownerId, from, until);
        long automationRuns = 0, automationSucceeded = 0, automationFailed = 0, automationRetried = 0;
        for (Object[] row : automation.personalAnalytics(ownerId, from, until)) {
            long count = number(row[1]);
            automationRuns += count;
            automationRetried += number(row[2]);
            AutomationExecutionStatus status = (AutomationExecutionStatus) row[0];
            if (status == AutomationExecutionStatus.SUCCEEDED) automationSucceeded += count;
            if (List.of(AutomationExecutionStatus.FAILED_RETRYABLE, AutomationExecutionStatus.FAILED_PERMANENT,
                    AutomationExecutionStatus.DEAD_LETTER).contains(status)) automationFailed += count;
        }

        List<AnalyticsProjectOption> options = projects.findAllByOwner_IdAndArchivedAtIsNullOrderByUpdatedAtDesc(ownerId).stream()
                .map(project -> new AnalyticsProjectOption(project.getId(), project.getName())).toList();
        return new AnalyticsOverview(start, end, zone, clock.instant(), projectId, options, created, completed, completionEvents,
                open, overdue, dueToday, dueNextSevenDays, noDueDate, actualWorkSeconds,
                projects.countByOwner_IdAndArchivedAtIsNullAndStatusIn(ownerId, List.of(ProjectStatus.ACTIVE)),
                goals.activeCount(ownerId), habits.activeCount(ownerId), aiPlanCount, verifiedAiTasks,
                automationRuns, automationSucceeded, automationFailed, automationRetried,
                List.copyOf(statusBuckets), List.copyOf(priorityBuckets), trend,
                completionEvents <= MAX_COMPLETIONS_FOR_TREND, DEFINITIONS);
    }

    private static List<AnalyticsBucket> withPercentages(List<AnalyticsBucket> values) {
        long max = values.stream().mapToLong(AnalyticsBucket::count).max().orElse(0);
        return values.stream().map(value -> new AnalyticsBucket(value.label(), value.count(),
                max == 0 || value.count() == 0 ? 0 : Math.max(5, (int) Math.round(value.count() * 100.0 / max)),
                value.href())).toList();
    }

    private static List<AnalyticsTrendPoint> trend(LocalDate start, LocalDate end, ZoneId zone, List<Instant> completedAt) {
        boolean daily = ChronoUnit.DAYS.between(start, end) < 45;
        Map<LocalDate, Long> counts = new TreeMap<>();
        LocalDate cursor = daily ? start : start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastBucket = daily ? end : end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (LocalDate bucket = cursor; !bucket.isAfter(lastBucket); bucket = bucket.plusDays(daily ? 1 : 7)) {
            counts.put(bucket, 0L);
        }
        for (Instant eventTime : completedAt) {
            LocalDate completedOn = eventTime.atZone(zone).toLocalDate();
            LocalDate bucket = daily ? completedOn : completedOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            counts.computeIfPresent(bucket, (ignored, count) -> count + 1);
        }
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<AnalyticsTrendPoint> points = new ArrayList<>();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("MMM d");
        for (var entry : counts.entrySet()) {
            LocalDate bucketEnd = daily ? entry.getKey() : entry.getKey().plusDays(6);
            LocalDate visibleStart = entry.getKey().isBefore(start) ? start : entry.getKey();
            LocalDate visibleEnd = bucketEnd.isAfter(end) ? end : bucketEnd;
            String label = visibleStart.equals(visibleEnd) ? format.format(visibleStart)
                    : format.format(visibleStart) + " – " + format.format(visibleEnd);
            int height = max == 0 || entry.getValue() == 0 ? 0 : Math.max(6, (int) Math.round(entry.getValue() * 100.0 / max));
            points.add(new AnalyticsTrendPoint(visibleStart, visibleEnd, label, entry.getValue(), height));
        }
        return List.copyOf(points);
    }

    private static AnalyticsMetricDefinition metric(String id, String name, String description,
            String source, String formula, String unit, String completeness) {
        return new AnalyticsMetricDefinition(id, name, description, source, formula, unit, completeness, METRIC_VERSION);
    }

    private static long number(Object value) { return value == null ? 0 : ((Number) value).longValue(); }
    private static String label(String name) { return name.toLowerCase(java.util.Locale.ROOT).replace('_', ' '); }
}
