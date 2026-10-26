package com.taskos.scheduling.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScheduleResult(Status status, LocalDate scheduleOrigin, LocalDate scheduledProjectFinish,
        long projectDurationDays, Map<UUID, TaskScheduleResult> taskResults, List<UUID> topologicalOrder,
        List<UUID> criticalTaskIds, List<UUID> criticalDependencyIds, List<String> missingInputs) {
    public enum Status { READY, INCOMPLETE }

    public ScheduleResult {
        taskResults = Map.copyOf(taskResults); topologicalOrder = List.copyOf(topologicalOrder);
        criticalTaskIds = List.copyOf(criticalTaskIds); criticalDependencyIds = List.copyOf(criticalDependencyIds);
        missingInputs = List.copyOf(missingInputs);
    }
}
