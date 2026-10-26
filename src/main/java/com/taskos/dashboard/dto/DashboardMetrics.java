package com.taskos.dashboard.dto;

public record DashboardMetrics(long taskRecordCount, long activeTaskCount, long dueTodayCount,
        long dueTomorrowCount, long dueNextSevenDaysCount, long overdueCount, long unscheduledCount,
        long completedTodayCount, long completedLastSevenDaysCount) {}
