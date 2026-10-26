package com.taskos.scheduling.service;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduleTaskInput(UUID id, String title, Integer durationDays, LocalDate earliestStartDate) { }
