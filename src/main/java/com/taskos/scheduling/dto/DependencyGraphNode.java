package com.taskos.scheduling.dto;

import java.util.UUID;

public record DependencyGraphNode(UUID id, String title, String status, Integer durationDays,
        boolean critical, double positionPercent, int topPixels) { }
