package com.taskos.time.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarPage(String view, LocalDate selectedDate, LocalDate today, String title, String timezone,
        LocalDate previousDate, LocalDate nextDate, List<CalendarDay> days, List<CalendarEvent> events) {
}
