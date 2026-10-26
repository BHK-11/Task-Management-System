package com.taskos.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record UpcomingPage(LocalDate today, List<DashboardTask> tomorrow,
        List<DashboardTask> nextSevenDays, List<DashboardTask> later,
        int page, boolean hasPrevious, boolean hasNext) {}
