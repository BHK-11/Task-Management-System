package com.taskos.analytics.dto;

import java.time.LocalDate;

public record AnalyticsTrendPoint(LocalDate startDate, LocalDate endDate, String label, long count, int heightPercent) { }
