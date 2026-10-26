package com.taskos.scheduling.service;

import java.time.LocalDate;
import java.util.UUID;

public record TaskScheduleResult(UUID taskId, long earliestStartOffset, long earliestFinishOffset,
        long latestStartOffset, long latestFinishOffset, long totalFloatDays, boolean critical,
        LocalDate earliestStart, LocalDate earliestFinish, LocalDate latestStart, LocalDate latestFinish) { }
