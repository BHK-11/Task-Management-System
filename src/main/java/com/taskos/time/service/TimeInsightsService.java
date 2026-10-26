package com.taskos.time.service;

import com.taskos.task.entity.Task;
import com.taskos.task.repository.TaskRepository;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.project.repository.ProjectTaskPlacementRepository;
import com.taskos.time.dto.TaskTimeTotal;
import com.taskos.time.dto.TimeInsightDay;
import com.taskos.time.dto.TimeInsights;
import com.taskos.time.entity.TimeBlock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeInsightsService {
    private final TimeTrackingService tracking;
    private final TimeBlockService blocks;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final ProjectTaskPlacementRepository placements;
    private final TimeZoneResolver zones;
    private final Clock clock;
    public TimeInsightsService(TimeTrackingService tracking, TimeBlockService blocks, TaskRepository tasks,
            ProjectRepository projects, ProjectTaskPlacementRepository placements, TimeZoneResolver zones, Clock clock) {
        this.tracking = tracking; this.blocks = blocks;
        this.tasks = tasks; this.projects = projects; this.placements = placements; this.zones = zones; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TimeInsights page(UUID ownerId) {
        ZoneId zone = zones.zone(ownerId); LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant dayStart = today.atStartOfDay(zone).toInstant(), dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant weekStartAt = weekStart.atStartOfDay(zone).toInstant(), weekEndAt = weekStart.plusDays(7).atStartOfDay(zone).toInstant();
        long todaySeconds = tracking.exclusiveSeconds(ownerId, dayStart, dayEnd);
        long weekSeconds = tracking.exclusiveSeconds(ownerId, weekStartAt, weekEndAt);
        long planned = blocks.range(ownerId, dayStart, dayEnd).stream().mapToLong(block -> overlapSeconds(block, dayStart, dayEnd)).sum();
        List<TimeInsightDay> days = new ArrayList<>();
        for (LocalDate date = weekStart; !date.isAfter(weekStart.plusDays(6)); date = date.plusDays(1)) {
            Instant start = date.atStartOfDay(zone).toInstant(), end = date.plusDays(1).atStartOfDay(zone).toInstant();
            days.add(new TimeInsightDay(date, tracking.exclusiveSeconds(ownerId, start, end)));
        }
        Map<UUID, Long> taskSeconds = tracking.taskSeconds(ownerId, weekStartAt, weekEndAt);
        Map<UUID, String> titles = tasks.findAllByOwner_IdAndIdIn(ownerId, taskSeconds.keySet()).stream()
                .collect(java.util.stream.Collectors.toMap(Task::getId, Task::getTitle));
        Map<UUID, String> projectByTask = new HashMap<>();
        if (!taskSeconds.isEmpty()) {
            Map<UUID, UUID> projectIds = placements.findAllByOwnerIdAndTaskIdIn(ownerId, taskSeconds.keySet()).stream()
                    .collect(java.util.stream.Collectors.toMap(com.taskos.project.entity.ProjectTaskPlacement::getTaskId,
                            com.taskos.project.entity.ProjectTaskPlacement::getProjectId));
            Map<UUID, String> projectNames = projectIds.isEmpty() ? Map.of()
                    : projects.findAllByOwner_IdAndIdIn(ownerId, projectIds.values()).stream()
                            .collect(java.util.stream.Collectors.toMap(com.taskos.project.entity.Project::getId,
                                    com.taskos.project.entity.Project::getName));
            projectIds.forEach((taskId, projectId) -> {
                if (projectNames.containsKey(projectId)) projectByTask.put(taskId, projectNames.get(projectId));
            });
        }
        List<TaskTimeTotal> byTask = taskSeconds.entrySet().stream().map(entry -> new TaskTimeTotal(entry.getKey(),
                titles.getOrDefault(entry.getKey(), "Deleted task"), entry.getValue(), projectByTask.get(entry.getKey())))
                .sorted(Comparator.comparingLong(TaskTimeTotal::seconds).reversed()).limit(10).toList();
        return new TimeInsights(todaySeconds, weekSeconds, planned, todaySeconds - planned, List.copyOf(days), byTask);
    }

    private static long overlapSeconds(TimeBlock block, Instant start, Instant end) {
        Instant a = block.getStartAt().isAfter(start) ? block.getStartAt() : start;
        Instant b = block.getEndAt().isBefore(end) ? block.getEndAt() : end;
        return b.isAfter(a) ? Duration.between(a, b).toSeconds() : 0;
    }
}
