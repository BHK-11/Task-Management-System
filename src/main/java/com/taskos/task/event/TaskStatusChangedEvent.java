package com.taskos.task.event;

import com.taskos.task.entity.TaskStatus;
import java.util.UUID;

public record TaskStatusChangedEvent(UUID ownerId, UUID taskId, TaskStatus previousStatus, TaskStatus status, UUID workspaceId) {
    public TaskStatusChangedEvent(UUID ownerId, UUID taskId, TaskStatus previousStatus, TaskStatus status) {
        this(ownerId, taskId, previousStatus, status, null);
    }
}
