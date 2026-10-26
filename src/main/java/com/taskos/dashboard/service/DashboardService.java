package com.taskos.dashboard.service;

import com.taskos.dashboard.dto.CompletionPoint;
import com.taskos.dashboard.dto.DashboardActivity;
import com.taskos.dashboard.dto.DashboardMetrics;
import com.taskos.dashboard.dto.DashboardMetricTotals;
import com.taskos.dashboard.dto.DashboardOverview;
import com.taskos.dashboard.dto.DashboardTask;
import com.taskos.dashboard.dto.TaskListPage;
import com.taskos.dashboard.dto.UpcomingPage;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskActivity;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.repository.TaskActivityRepository;
import com.taskos.task.repository.TaskRepository;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("UTC");
    private static final List<TaskStatus> ACTIVE_STATUSES = List.of(
            TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.ON_HOLD);
    private static final List<TaskPriority> HIGH_PRIORITIES = List.of(TaskPriority.URGENT, TaskPriority.HIGH);
    private static final int PAGE_SIZE = 30;
    private static final int MAX_PAGE = 1000;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);

    private final TaskRepository tasks;
    private final TaskActivityRepository activity;
    private final UserAccountRepository users;
    private final Clock clock;

    public DashboardService(TaskRepository tasks, TaskActivityRepository activity,
            UserAccountRepository users, Clock clock) {
        this.tasks = tasks;
        this.activity = activity;
        this.users = users;
        this.clock = clock;
    }

    public DashboardOverview overview(UUID ownerId) {
        ZonedDateTime now = nowFor(ownerId);
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate weekEndExclusive = today.plusDays(8);
        LocalDate weekStart = today.minusDays(6);
        ZoneId zone = now.getZone();

        DashboardMetricTotals totals = tasks.dashboardMetricTotals(ownerId, ACTIVE_STATUSES, TaskStatus.COMPLETED,
                today, tomorrow, weekEndExclusive, weekStart.atStartOfDay(zone).toInstant(),
                today.atStartOfDay(zone).toInstant(), tomorrow.atStartOfDay(zone).toInstant());
        DashboardMetrics metrics = new DashboardMetrics(
                tasks.countByOwner_Id(ownerId),
                totals.activeTaskCount(), totals.dueTodayCount(), totals.dueTomorrowCount(),
                totals.dueNextSevenDaysCount(), totals.overdueCount(), totals.unscheduledCount(),
                totals.completedTodayCount(), totals.completedLastSevenDaysCount());

        List<Task> myDay = tasks.findDashboardMyDay(ownerId, ACTIVE_STATUSES, today, HIGH_PRIORITIES,
                TaskPriority.URGENT, TaskPriority.HIGH, PageRequest.of(0, 8)).getContent();
        List<Task> priorities = tasks.findDashboardPriorities(ownerId, ACTIVE_STATUSES, HIGH_PRIORITIES,
                TaskPriority.URGENT, PageRequest.of(0, 5));
        List<Task> upcoming = tasks.findDashboardUpcoming(ownerId, ACTIVE_STATUSES, tomorrow,
                TaskPriority.URGENT, TaskPriority.HIGH, PageRequest.of(0, 5)).getContent();
        List<Task> recentlyCompleted = tasks.findDashboardRecentlyCompleted(ownerId, TaskStatus.COMPLETED,
                PageRequest.of(0, 5));

        return new DashboardOverview(greeting(now), DATE_FORMAT.format(now), now, today, zone, metrics,
                mapTasks(ownerId, myDay, today), mapTasks(ownerId, priorities, today),
                mapTasks(ownerId, upcoming, today), mapTasks(ownerId, recentlyCompleted, today),
                recentActivity(ownerId, zone), completionTrend(ownerId, today, zone));
    }

    public TaskListPage myDay(UUID ownerId, int requestedPage) {
        ZonedDateTime now = nowFor(ownerId);
        LocalDate today = now.toLocalDate();
        Page<Task> result = tasks.findDashboardMyDay(ownerId, ACTIVE_STATUSES, today, HIGH_PRIORITIES,
                TaskPriority.URGENT, TaskPriority.HIGH, pageRequest(requestedPage));
        return new TaskListPage("My Day", "Open tasks due today or earlier, followed by urgent and high-priority work.",
                "Your day is clear", "There are no overdue, due-today, or high-priority active tasks.",
                today, mapTasks(ownerId, result.getContent(), today), result.getNumber(), result.hasPrevious(), result.hasNext());
    }

    public TaskListPage today(UUID ownerId, int requestedPage) {
        ZonedDateTime now = nowFor(ownerId);
        LocalDate today = now.toLocalDate();
        Page<Task> result = tasks.findDashboardDueOn(ownerId, ACTIVE_STATUSES, today,
                TaskPriority.URGENT, TaskPriority.HIGH, pageRequest(requestedPage));
        return new TaskListPage("Due Today", "Active top-level tasks with a due date of " + DATE_FORMAT.format(now) + ".",
                "Nothing due today", "You're clear of today's scheduled deadlines. Other active tasks may still be unscheduled.",
                today, mapTasks(ownerId, result.getContent(), today), result.getNumber(), result.hasPrevious(), result.hasNext());
    }

    public TaskListPage tomorrow(UUID ownerId, int requestedPage) {
        ZonedDateTime now = nowFor(ownerId);
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        Page<Task> result = tasks.findDashboardDueOn(ownerId, ACTIVE_STATUSES, tomorrow,
                TaskPriority.URGENT, TaskPriority.HIGH, pageRequest(requestedPage));
        return new TaskListPage("Due Tomorrow", "Active top-level tasks due "
                + DATE_FORMAT.format(tomorrow.atStartOfDay(now.getZone())) + ".",
                "Nothing due tomorrow", "There are no active tasks scheduled for tomorrow.", now.toLocalDate(),
                mapTasks(ownerId, result.getContent(), now.toLocalDate()), result.getNumber(), result.hasPrevious(), result.hasNext());
    }

    public TaskListPage overdue(UUID ownerId, int requestedPage) {
        ZonedDateTime now = nowFor(ownerId);
        LocalDate today = now.toLocalDate();
        Page<Task> result = tasks.findDashboardOverdue(ownerId, ACTIVE_STATUSES, today,
                TaskPriority.URGENT, TaskPriority.HIGH, pageRequest(requestedPage));
        return new TaskListPage("Overdue", "Active top-level tasks with a due date before "
                + DATE_FORMAT.format(now) + ". Completed, cancelled, archived, and child tasks are excluded.",
                "No overdue tasks", "There are no active tasks with a past due date.", today,
                mapTasks(ownerId, result.getContent(), today), result.getNumber(), result.hasPrevious(), result.hasNext());
    }

    public UpcomingPage upcoming(UUID ownerId, int requestedPage) {
        ZonedDateTime now = nowFor(ownerId);
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate nextSevenDaysEnd = today.plusDays(7);
        Page<Task> result = tasks.findDashboardUpcoming(ownerId, ACTIVE_STATUSES, tomorrow,
                TaskPriority.URGENT, TaskPriority.HIGH, pageRequest(requestedPage));
        List<Task> tomorrowTasks = new ArrayList<>();
        List<Task> weekTasks = new ArrayList<>();
        List<Task> laterTasks = new ArrayList<>();
        for (Task task : result.getContent()) {
            if (task.getDueDate().equals(tomorrow)) tomorrowTasks.add(task);
            else if (!task.getDueDate().isAfter(nextSevenDaysEnd)) weekTasks.add(task);
            else laterTasks.add(task);
        }
        return new UpcomingPage(today, mapTasks(ownerId, tomorrowTasks, today), mapTasks(ownerId, weekTasks, today),
                mapTasks(ownerId, laterTasks, today), result.getNumber(), result.hasPrevious(), result.hasNext());
    }

    private List<DashboardActivity> recentActivity(UUID ownerId, ZoneId zone) {
        List<TaskActivity> events = activity.findAllByOwner_IdOrderByOccurredAtDesc(ownerId, PageRequest.of(0, 8));
        List<UUID> taskIds = events.stream().map(TaskActivity::getTaskId).filter(id -> id != null).distinct().toList();
        Map<UUID, String> titles = taskIds.isEmpty() ? Map.of() : tasks.findAllByOwner_IdAndIdIn(ownerId, taskIds)
                .stream().collect(Collectors.toMap(Task::getId, Task::getTitle));
        return events.stream().map(event -> new DashboardActivity(event.getTaskId(),
                event.getTaskId() == null ? null : titles.getOrDefault(event.getTaskId(), "Task no longer available"),
                event.getSummary(), event.getEventType(), event.getOccurredAt().atZone(zone))).toList();
    }

    private List<CompletionPoint> completionTrend(UUID ownerId, LocalDate today, ZoneId zone) {
        List<Long> counts = new ArrayList<>(7);
        long max = 0;
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            long count = tasks.countDashboardCompletedBetween(ownerId, TaskStatus.COMPLETED,
                    date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
            counts.add(count);
            max = Math.max(max, count);
        }
        long maximum = max;
        List<CompletionPoint> points = new ArrayList<>(7);
        for (int index = 0; index < 7; index++) {
            LocalDate date = today.minusDays(6L - index);
            long count = counts.get(index);
            int height = count == 0 || maximum == 0 ? 0 : Math.max(8, (int) Math.round(count * 100.0 / maximum));
            String label = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            points.add(new CompletionPoint(date, label, count, height));
        }
        return List.copyOf(points);
    }

    private List<DashboardTask> mapTasks(UUID ownerId, List<Task> selected, LocalDate today) {
        if (selected.isEmpty()) return List.of();
        List<UUID> ids = selected.stream().map(Task::getId).toList();
        Map<UUID, long[]> children = new HashMap<>();
        for (Object[] row : tasks.countChildrenByParentIds(ownerId, ids)) {
            UUID parentId = (UUID) row[0];
            TaskStatus status = (TaskStatus) row[1];
            long count = ((Number) row[2]).longValue();
            long[] values = children.computeIfAbsent(parentId, ignored -> new long[2]);
            values[0] += count;
            if (status == TaskStatus.COMPLETED) values[1] += count;
        }
        return selected.stream().map(task -> {
            long[] subtasks = children.getOrDefault(task.getId(), new long[2]);
            long daysOverdue = task.getDueDate() != null && task.getDueDate().isBefore(today)
                    ? ChronoUnit.DAYS.between(task.getDueDate(), today) : 0;
            return new DashboardTask(task.getId(), task.getTitle(), task.getPriority(), task.getStatus(), task.getDueDate(),
                    subtasks[0], subtasks[1], daysOverdue, reason(task, today));
        }).toList();
    }

    private String reason(Task task, LocalDate today) {
        if (task.getStatus() == TaskStatus.COMPLETED) return "Recently completed";
        if (task.getDueDate() != null && task.getDueDate().isBefore(today)) return "Overdue";
        if (today.equals(task.getDueDate())) return "Due today";
        if (task.getPriority() == TaskPriority.URGENT) return "Urgent priority";
        if (task.getPriority() == TaskPriority.HIGH) return "High priority";
        if (task.getDueDate() != null && task.getDueDate().isEqual(today.plusDays(1))) return "Due tomorrow";
        if (task.getDueDate() != null && task.getDueDate().isAfter(today.plusDays(1))) return "Upcoming";
        return "Active task";
    }

    private ZonedDateTime nowFor(UUID ownerId) {
        UserAccount account = users.findById(ownerId).orElseThrow();
        ZoneId zone;
        try {
            zone = ZoneId.of(account.getTimezone());
        } catch (DateTimeException | NullPointerException ex) {
            zone = FALLBACK_ZONE;
        }
        return clock.instant().atZone(zone);
    }

    private String greeting(ZonedDateTime now) {
        int hour = now.getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private PageRequest pageRequest(int requestedPage) {
        int page = Math.max(0, Math.min(requestedPage, MAX_PAGE));
        return PageRequest.of(page, PAGE_SIZE);
    }
}
