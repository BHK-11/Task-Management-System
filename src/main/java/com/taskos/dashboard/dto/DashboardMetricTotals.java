package com.taskos.dashboard.dto;

public record DashboardMetricTotals(Long activeTaskCount, Long dueTodayCount, Long dueTomorrowCount,
        Long dueNextSevenDaysCount, Long overdueCount, Long unscheduledCount,
        Long completedTodayCount, Long completedLastSevenDaysCount) {}
