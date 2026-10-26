package com.taskos.goals.dto;

import com.taskos.goals.entity.Goal;
import com.taskos.goals.entity.GoalMeasurement;
import com.taskos.goals.entity.GoalMilestone;
import com.taskos.task.entity.Task;
import com.taskos.project.entity.Project;
import java.util.List;

public record GoalDetail(Goal goal, GoalProgress progress, List<GoalMeasurement> measurements,
        List<GoalMilestone> milestones, List<Task> tasks, Project project) { }
