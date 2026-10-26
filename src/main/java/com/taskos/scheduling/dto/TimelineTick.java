package com.taskos.scheduling.dto;

import java.time.LocalDate;

public record TimelineTick(LocalDate date, String label, double positionPercent) { }
