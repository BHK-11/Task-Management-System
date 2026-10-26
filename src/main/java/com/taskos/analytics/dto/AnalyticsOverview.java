package com.taskos.analytics.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record AnalyticsOverview(LocalDate start, LocalDate end, ZoneId timezone, Instant generatedAt,
        UUID projectId, List<AnalyticsProjectOption> projects,
        long createdTasks, long completedTasks, long completionEvents, long openTasks, long overdueTasks,
        long dueTodayTasks, long dueNextSevenDaysTasks, long noDueDateTasks,
        Long actualWorkSeconds, long activeProjects, long activeGoals, long activeHabits,
        long aiPlans, long verifiedAiTasks, long automationRuns, long automationSucceeded,
        long automationFailed, long automationRetried,
        List<AnalyticsBucket> statusDistribution, List<AnalyticsBucket> openPriorityDistribution,
        List<AnalyticsTrendPoint> completionTrend, boolean completionTrendAvailable,
        List<AnalyticsMetricDefinition> definitions) { }
