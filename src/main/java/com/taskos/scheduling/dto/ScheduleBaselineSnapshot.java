package com.taskos.scheduling.dto;

import com.taskos.scheduling.entity.TaskDependencyType;
import com.taskos.scheduling.service.ScheduleResult;
import com.taskos.scheduling.service.TaskScheduleResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ScheduleBaselineSnapshot(int version, LocalDate origin, ScheduleResult calculation,
        List<Task> tasks, List<Dependency> dependencies, List<Milestone> milestones) {
    public ScheduleBaselineSnapshot {
        tasks = List.copyOf(tasks); dependencies = List.copyOf(dependencies); milestones = List.copyOf(milestones);
    }

    public record Task(UUID id, String title, String status, LocalDate plannedStart, LocalDate dueDate,
            Integer durationDays, TaskScheduleResult calculation) { }
    public record Dependency(UUID id, UUID predecessorId, UUID successorId, TaskDependencyType type, int lagDays) { }
    public record Milestone(UUID id, String title, String status, LocalDate targetDate, Instant completedAt) { }
}
