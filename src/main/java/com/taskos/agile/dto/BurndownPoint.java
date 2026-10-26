package com.taskos.agile.dto;

import java.time.LocalDate;

public record BurndownPoint(LocalDate date, Integer actualRemainingPoints, int idealRemainingPoints) { }
