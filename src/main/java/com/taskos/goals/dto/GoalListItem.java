package com.taskos.goals.dto;

import com.taskos.goals.entity.Goal;

public record GoalListItem(Goal goal, GoalProgress progress) { }
