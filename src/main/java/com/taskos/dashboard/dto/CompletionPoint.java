package com.taskos.dashboard.dto;

import java.time.LocalDate;

public record CompletionPoint(LocalDate date, String dayLabel, long taskCount, int barHeight) {}
