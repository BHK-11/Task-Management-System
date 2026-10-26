package com.taskos.habits.dto;

import com.taskos.habits.entity.Habit;

public record HabitListItem(Habit habit, HabitStats stats) { }
