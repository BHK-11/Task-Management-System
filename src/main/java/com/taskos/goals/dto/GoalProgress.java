package com.taskos.goals.dto;

import java.math.BigDecimal;

public record GoalProgress(BigDecimal currentValue, BigDecimal progress, int displayPercent, String label) { }
