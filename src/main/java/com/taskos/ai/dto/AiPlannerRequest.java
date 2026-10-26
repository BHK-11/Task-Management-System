package com.taskos.ai.dto;

import java.time.LocalDate;
import java.util.List;

public record AiPlannerRequest(String instruction, String timezone, LocalDate today, String mode,
        String provider, String model, boolean allowCloudProcessing, boolean allowFallback, List<String> memoryContext) {}
