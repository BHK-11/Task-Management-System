package com.taskos.scheduling.service;

import com.taskos.scheduling.entity.TaskDependencyType;
import java.util.UUID;

public record ScheduleDependencyInput(UUID id, UUID predecessorId, UUID successorId,
        TaskDependencyType type, int lagDays) { }
