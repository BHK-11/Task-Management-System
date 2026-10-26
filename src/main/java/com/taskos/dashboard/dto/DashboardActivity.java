package com.taskos.dashboard.dto;

import com.taskos.task.entity.TaskActivityType;
import java.time.ZonedDateTime;
import java.util.UUID;

public record DashboardActivity(UUID taskId, String taskTitle, String summary,
        TaskActivityType eventType, ZonedDateTime occurredAt) {
    public boolean taskAvailable() {
        return taskId != null && taskTitle != null && !taskTitle.equals("Task no longer available");
    }
}
