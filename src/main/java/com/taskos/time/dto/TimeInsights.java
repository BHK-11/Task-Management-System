package com.taskos.time.dto;

import java.util.List;

public record TimeInsights(long todaySeconds, long weekSeconds, long plannedTodaySeconds,
        long actualMinusPlannedSeconds, List<TimeInsightDay> weekDays, List<TaskTimeTotal> byTask) { }
