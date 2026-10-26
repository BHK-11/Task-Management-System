package com.taskos.task.event;

import com.taskos.task.entity.TaskActivityType;
import java.util.UUID;

public record TaskActivityRecordedEvent(UUID ownerId, UUID taskId, TaskActivityType type, String summary) { }
