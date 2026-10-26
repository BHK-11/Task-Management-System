package com.taskos.project.dto;

import com.taskos.project.entity.BoardStage;
import com.taskos.project.entity.WipPolicy;
import com.taskos.task.entity.TaskPriority;
import java.util.List;

public record ProjectTemplateDefinition(int version, List<Column> columns,
        List<Milestone> milestones, List<TaskBlueprint> tasks) {
    public record Column(String name, BoardStage stage, int position, Integer wipLimit, WipPolicy wipPolicy) { }
    public record Milestone(String title, String description, Integer dueInDays, int position) { }
    public record TaskBlueprint(String title, String description, TaskPriority priority,
            int columnPosition, Integer milestonePosition, Integer dueInDays) { }
}
