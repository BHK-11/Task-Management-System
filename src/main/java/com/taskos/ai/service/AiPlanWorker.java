package com.taskos.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "taskos.ai.execution.enabled", havingValue = "true", matchIfMissing = true)
public class AiPlanWorker {
    private final AiPlanExecutionService plans;

    public AiPlanWorker(AiPlanExecutionService plans) { this.plans = plans; }

    @Scheduled(fixedDelay = 1_000)
    public void runNext() { plans.executeNext(); }
}
