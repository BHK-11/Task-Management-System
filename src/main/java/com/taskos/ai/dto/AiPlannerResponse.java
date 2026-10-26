package com.taskos.ai.dto;

public record AiPlannerResponse(AiPlannerPlanDraft plan, String model, Integer inputTokens, Integer outputTokens) {}
