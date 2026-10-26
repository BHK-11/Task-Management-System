package com.taskos.collaboration.dto;

import java.time.LocalDate;
import java.util.List;

public record WorkspaceCalendarPage(LocalDate selectedMonth, LocalDate previousMonth, LocalDate nextMonth,
        String title, List<WorkspaceCalendarDay> days) { }
