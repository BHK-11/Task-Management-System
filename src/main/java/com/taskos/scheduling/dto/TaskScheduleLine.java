package com.taskos.scheduling.dto;

import com.taskos.scheduling.service.TaskScheduleResult;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskScheduleLine(UUID id, String title, TaskStatus status, TaskPriority priority,
        LocalDate plannedStart, LocalDate dueDate, Integer durationDays, long version, Instant completedAt,
        UUID milestoneId, String milestoneTitle, String sprintName, LocalDate sprintStart, LocalDate sprintEnd,
        String sprintGoal, TaskScheduleResult calculation,
        Double barStartPercent, Double barWidthPercent, Double baselineStartPercent, Double baselineWidthPercent) { }
