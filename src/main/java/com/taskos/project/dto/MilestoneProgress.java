package com.taskos.project.dto;

import com.taskos.project.entity.ProjectMilestone;

public record MilestoneProgress(ProjectMilestone milestone, long totalTasks, long completedTasks, int percent) { }
