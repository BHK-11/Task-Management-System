package com.taskos.scheduling.dto;

import com.taskos.project.entity.MilestoneStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MilestoneLine(UUID id, String title, MilestoneStatus status, LocalDate targetDate,
        Instant completedAt, Double markerPercent) { }
