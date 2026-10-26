package com.taskos.ai.dto;

import com.taskos.task.entity.TaskPriority;
import java.time.LocalDate;
import java.util.List;

public record AiPlannerStepDraft(AiActionType actionType, String title, String description,
        TaskPriority priority, LocalDate dueDate, List<Integer> dependencies) {}
