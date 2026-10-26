package com.taskos.ai.service;

import com.taskos.ai.dto.AiPlannerResponse;
import com.taskos.ai.dto.AiWorkPlannerResponse;
import com.taskos.ai.entity.AiPlan;
import com.taskos.ai.repository.AiPlanRepository;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiPlanPersistenceService {
    private final AiPlanRepository plans;
    private final UserAccountRepository users;

    public AiPlanPersistenceService(AiPlanRepository plans, UserAccountRepository users) {
        this.plans = plans;
        this.users = users;
    }

    @Transactional
    public AiPlan save(UUID ownerId, String instruction, AiPlannerResponse response, Instant now) {
        var owner = users.findById(ownerId).orElseThrow(() -> new AiCopilotException("Your account is unavailable."));
        return plans.saveAndFlush(AiPlan.create(owner, instruction, response, now));
    }

    @Transactional
    public AiPlan saveWork(UUID ownerId, String instruction, AiWorkPlannerResponse response, Instant now) {
        var owner = users.findById(ownerId).orElseThrow(() -> new AiCopilotException("Your account is unavailable."));
        return plans.saveAndFlush(AiPlan.createWork(owner, instruction, response, now));
    }
}
