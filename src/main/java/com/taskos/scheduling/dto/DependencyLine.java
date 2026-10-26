package com.taskos.scheduling.dto;

import com.taskos.scheduling.entity.TaskDependencyType;
import java.util.UUID;

public record DependencyLine(UUID id, UUID predecessorId, UUID successorId, String predecessorTitle,
        String successorTitle, TaskDependencyType type, int lagDays, boolean critical) { }
