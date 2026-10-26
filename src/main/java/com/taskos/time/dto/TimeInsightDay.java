package com.taskos.time.dto;

import java.time.LocalDate;

public record TimeInsightDay(LocalDate date, long seconds) {
    public int barHeight() { return seconds == 0 ? 0 : (int) Math.max(8, Math.min(100, Math.round(seconds / 3600.0 * 12))); }
}
