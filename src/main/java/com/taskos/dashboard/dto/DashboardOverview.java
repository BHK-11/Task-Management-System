package com.taskos.dashboard.dto;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public record DashboardOverview(String greeting, String displayDate, ZonedDateTime localNow,
        LocalDate today, ZoneId zone, DashboardMetrics metrics, List<DashboardTask> myDay,
        List<DashboardTask> priorities, List<DashboardTask> upcoming, List<DashboardTask> recentlyCompleted,
        List<DashboardActivity> recentActivity, List<CompletionPoint> completionTrend) {
    public boolean hasTasks() {
        return metrics.taskRecordCount() > 0;
    }
}
