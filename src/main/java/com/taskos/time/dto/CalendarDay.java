package com.taskos.time.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarDay(LocalDate date, boolean currentMonth, boolean today, List<CalendarEvent> events) {
    public List<CalendarEvent> visibleEvents() { return events.size() <= 3 ? events : events.subList(0, 3); }
    public int moreCount() { return Math.max(0, events.size() - 3); }
    public List<CalendarEvent> allDayEvents() { return events.stream().filter(CalendarEvent::allDay).toList(); }
    public List<CalendarEvent> timedEvents() { return events.stream().filter(event -> !event.allDay()).toList(); }
}
