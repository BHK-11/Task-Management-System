package com.taskos.collaboration.dto;

import com.taskos.task.entity.Task;
import java.time.LocalDate;
import java.util.List;

public record WorkspaceCalendarDay(LocalDate date, boolean inMonth, boolean today, List<Task> tasks) { }
