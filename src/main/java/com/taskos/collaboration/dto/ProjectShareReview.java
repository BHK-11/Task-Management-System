package com.taskos.collaboration.dto;

import com.taskos.project.entity.Project;
import com.taskos.project.entity.ProjectTaskPlacement;
import java.util.List;

public record ProjectShareReview(Project project, List<ProjectTaskPlacement> tasks, long existingActivityCount,
        boolean shareable, String restriction) {
    public int taskCount() { return tasks.size(); }
}
