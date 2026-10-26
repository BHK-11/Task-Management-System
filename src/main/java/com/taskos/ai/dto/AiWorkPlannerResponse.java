package com.taskos.ai.dto;

public record AiWorkPlannerResponse(AiWorkPlannerPlanDraft plan, String model, Integer inputTokens, Integer outputTokens) {}
