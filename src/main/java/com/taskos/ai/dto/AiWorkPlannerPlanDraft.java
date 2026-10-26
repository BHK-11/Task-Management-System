package com.taskos.ai.dto;

import java.util.List;

public record AiWorkPlannerPlanDraft(String summary, String detectedLanguage, List<String> clarifications,
        List<AiWorkPlannerStepDraft> steps) {}
