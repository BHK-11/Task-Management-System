package com.taskos.habits.dto;

import java.math.BigDecimal;

public record HabitStats(int successfulPeriods, int missedPeriods, int currentStreak, int longestStreak,
        BigDecimal consistencyPercent, int eligibleClosedPeriods, int openPeriods) { }
