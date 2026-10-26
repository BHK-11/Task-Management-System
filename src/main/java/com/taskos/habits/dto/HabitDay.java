package com.taskos.habits.dto;

import java.time.LocalDate;

public record HabitDay(LocalDate date, String state, String label) { }
