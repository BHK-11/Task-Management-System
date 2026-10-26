package com.taskos.scheduling.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduleImpactLine(UUID taskId, String title, LocalDate previousStart, LocalDate proposedStart,
        LocalDate previousFinish, LocalDate proposedFinish, Integer durationChangeDays,
        long floatChangeDays, boolean criticalAfterChange) { }
