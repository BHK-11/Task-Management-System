package com.taskos.time.service;

import com.taskos.project.entity.ProjectMilestone;
import com.taskos.project.repository.ProjectMilestoneRepository;
import com.taskos.task.entity.Task;
import com.taskos.task.repository.TaskRepository;
import com.taskos.time.dto.CalendarDay;
import com.taskos.time.dto.CalendarEvent;
import com.taskos.time.dto.CalendarPage;
import com.taskos.time.entity.TimeBlock;
import com.taskos.time.repository.TimeBlockRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarQueryService {
    private final TaskRepository tasks;
    private final TimeBlockRepository blocks;
    private final ProjectMilestoneRepository milestones;
    private final TimeZoneResolver zones;
    private final Clock clock;

    public CalendarQueryService(TaskRepository tasks, TimeBlockRepository blocks,
            ProjectMilestoneRepository milestones, TimeZoneResolver zones, Clock clock) {
        this.tasks = tasks; this.blocks = blocks; this.milestones = milestones; this.zones = zones; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CalendarPage page(UUID ownerId, String requestedView, LocalDate requestedDate) {
        ZoneId zone = zones.zone(ownerId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        String view = List.of("month", "week", "day").contains(requestedView) ? requestedView : "month";
        LocalDate selected = requestedDate == null ? today : requestedDate;
        LocalDate rangeStart, rangeEnd;
        String title;
        if (view.equals("month")) {
            LocalDate first = selected.withDayOfMonth(1), last = selected.with(TemporalAdjusters.lastDayOfMonth());
            rangeStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            rangeEnd = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            title = selected.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        } else if (view.equals("week")) {
            rangeStart = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            rangeEnd = rangeStart.plusDays(6);
            title = rangeStart.format(DateTimeFormatter.ofPattern("d MMM")) + " – " + rangeEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy"));
        } else {
            rangeStart = selected; rangeEnd = selected;
            title = selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"));
        }

        List<CalendarEvent> events = new ArrayList<>();
        tasks.findAllByOwner_IdAndParentIsNullAndArchivedAtIsNullAndDueDateGreaterThanEqualAndDueDateLessThanEqualOrderByDueDateAsc(
                ownerId, rangeStart, rangeEnd, PageRequest.of(0, 250)).getContent().forEach(task -> events.add(deadline(task)));
        for (ProjectMilestone milestone : milestones.findCalendarMilestones(ownerId, rangeStart, rangeEnd)) {
            events.add(new CalendarEvent("MILESTONE", milestone.getTitle(), milestone.getTargetDate(), null, null,
                    null, null, "/projects/" + milestone.getProjectId(), false, 0));
        }
        Instant rangeStartAt = rangeStart.atStartOfDay(zone).toInstant();
        Instant rangeEndAt = rangeEnd.plusDays(1).atStartOfDay(zone).toInstant();
        List<TimeBlock> visibleBlocks = blocks.findAllByOwnerIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                ownerId, rangeEndAt, rangeStartAt);
        for (TimeBlock block : visibleBlocks) addBlockEvents(events, block, zone, rangeStart, rangeEnd);
        events.sort(Comparator.comparing(CalendarEvent::date)
                .thenComparing(event -> event.start() == null ? LocalTime.MIN : event.start().toLocalTime())
                .thenComparing(CalendarEvent::title, String.CASE_INSENSITIVE_ORDER));

        Map<LocalDate, List<CalendarEvent>> byDate = new HashMap<>();
        events.forEach(event -> byDate.computeIfAbsent(event.date(), ignored -> new ArrayList<>()).add(event));
        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate day = rangeStart; !day.isAfter(rangeEnd); day = day.plusDays(1)) {
            days.add(new CalendarDay(day, day.getMonth() == selected.getMonth(), day.equals(today),
                    List.copyOf(byDate.getOrDefault(day, List.of()))));
        }
        LocalDate previous = view.equals("month") ? selected.minusMonths(1) : view.equals("week") ? selected.minusWeeks(1) : selected.minusDays(1);
        LocalDate next = view.equals("month") ? selected.plusMonths(1) : view.equals("week") ? selected.plusWeeks(1) : selected.plusDays(1);
        return new CalendarPage(view, selected, today, title, zone.getId(), previous, next, List.copyOf(days), List.copyOf(events));
    }

    @Transactional(readOnly = true)
    public List<Task> taskOptions(UUID ownerId) {
        return tasks.findProductivityTasks(ownerId, List.of(com.taskos.task.entity.TaskStatus.TODO,
                com.taskos.task.entity.TaskStatus.IN_PROGRESS, com.taskos.task.entity.TaskStatus.ON_HOLD), PageRequest.of(0, 100));
    }

    private static CalendarEvent deadline(Task task) {
        return new CalendarEvent("TASK_DEADLINE", task.getTitle(), task.getDueDate(), null, null,
                task.getId(), null, "/tasks/" + task.getId(), false, 0);
    }

    private static void addBlockEvents(List<CalendarEvent> events, TimeBlock block, ZoneId zone,
            LocalDate rangeStart, LocalDate rangeEnd) {
        LocalDate first = block.getStartAt().atZone(zone).toLocalDate();
        LocalDate last = block.getEndAt().minusNanos(1).atZone(zone).toLocalDate();
        for (LocalDate day = first.isBefore(rangeStart) ? rangeStart : first;
                !day.isAfter(last) && !day.isAfter(rangeEnd); day = day.plusDays(1)) {
            Instant dayStart = day.atStartOfDay(zone).toInstant();
            Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
            Instant clippedStart = block.getStartAt().isAfter(dayStart) ? block.getStartAt() : dayStart;
            Instant clippedEnd = block.getEndAt().isBefore(dayEnd) ? block.getEndAt() : dayEnd;
            LocalDateTime start = clippedStart.atZone(zone).toLocalDateTime();
            LocalDateTime end = clippedEnd.atZone(zone).toLocalDateTime();
            events.add(new CalendarEvent("TIME_BLOCK", block.getTitle(), day, start, end, block.getTaskId(),
                    block.getId(), "/time/blocks?edit=" + block.getId(), false, block.getVersion()));
        }
    }
}
