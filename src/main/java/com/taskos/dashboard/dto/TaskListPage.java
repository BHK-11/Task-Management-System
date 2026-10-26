package com.taskos.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record TaskListPage(String title, String description, String emptyTitle, String emptyDescription,
        LocalDate today, List<DashboardTask> tasks, int page, boolean hasPrevious, boolean hasNext) {}
