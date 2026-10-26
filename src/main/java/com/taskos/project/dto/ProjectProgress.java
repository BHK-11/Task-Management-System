package com.taskos.project.dto;

import java.util.Map;

public record ProjectProgress(long totalTasks, long completedTasks, long openTasks, long overdueTasks,
        int completionPercent, long totalMilestones, long completedMilestones, String health,
        Map<String, Long> tasksByStatus) { }
