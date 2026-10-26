package com.taskos.time.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CalendarEvent(String type, String title, LocalDate date, LocalDateTime start,
        LocalDateTime end, UUID taskId, UUID timeBlockId, String href, boolean overlap, long version) {
    public boolean allDay() { return start == null; }
    public String timeLabel() { return start == null ? "All day" : start.toLocalTime() + (end == null ? "" : "–" + end.toLocalTime()); }
}
