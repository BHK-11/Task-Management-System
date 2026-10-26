package com.taskos.habits.dto;

import com.taskos.habits.entity.Habit;
import com.taskos.habits.entity.HabitCheckIn;
import com.taskos.habits.entity.HabitPause;
import com.taskos.habits.entity.HabitSchedule;
import java.util.List;

public record HabitDetail(Habit habit, HabitStats stats, List<HabitCheckIn> checkIns,
        List<HabitSchedule> schedules, List<HabitPause> pauses, List<HabitDay> calendar) { }
