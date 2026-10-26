package com.taskos.scheduling.dto;

import com.taskos.scheduling.service.ScheduleResult;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ScheduleChangePreview(UUID taskId, String taskTitle, Integer proposedDurationDays,
        LocalDate proposedStartDate, long projectVersion, long taskVersion,
        ScheduleResult current, ScheduleResult proposed, List<ScheduleImpactLine> impacts,
        boolean canConfirm, String message) {
    public ScheduleChangePreview { impacts = List.copyOf(impacts); }
}
