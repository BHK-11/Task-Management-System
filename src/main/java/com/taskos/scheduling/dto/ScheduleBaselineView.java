package com.taskos.scheduling.dto;

import com.taskos.scheduling.entity.ScheduleBaseline;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleBaselineView(UUID id, String name, Instant createdAt, LocalDate finishDate,
        Long durationDays) {
    public static ScheduleBaselineView from(ScheduleBaseline baseline, ScheduleBaselineSnapshot snapshot) {
        boolean ready = snapshot.calculation().status() == com.taskos.scheduling.service.ScheduleResult.Status.READY;
        return new ScheduleBaselineView(baseline.getId(), baseline.getName(), baseline.getCreatedAt(),
                snapshot.calculation().scheduledProjectFinish(), ready ? snapshot.calculation().projectDurationDays() : null);
    }
}
