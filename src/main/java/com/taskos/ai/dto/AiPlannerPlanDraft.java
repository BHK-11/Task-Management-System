package com.taskos.ai.dto;

import java.util.List;

public record AiPlannerPlanDraft(String summary, String detectedLanguage, List<String> clarifications,
        List<AiPlannerStepDraft> steps) {}
