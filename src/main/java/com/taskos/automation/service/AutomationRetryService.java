package com.taskos.automation.service;

import com.taskos.automation.entity.AutomationExecutionStatus;
import com.taskos.automation.entity.AutomationRuleStatus;
import com.taskos.automation.entity.AutomationScopeType;
import com.taskos.automation.repository.AutomationExecutionRepository;
import com.taskos.automation.repository.AutomationRuleRepository;
import com.taskos.automation.repository.AutomationStepExecutionRepository;
import com.taskos.collaboration.service.WorkspaceAuthorizationService;
import com.taskos.collaboration.service.WorkspacePermission;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationRetryService {
    private final AutomationExecutionRepository executions;
    private final AutomationRuleRepository rules;
    private final AutomationStepExecutionRepository steps;
    private final WorkspaceAuthorizationService authorization;
    private final Clock clock;

    public AutomationRetryService(AutomationExecutionRepository executions, AutomationRuleRepository rules,
            AutomationStepExecutionRepository steps, WorkspaceAuthorizationService authorization, Clock clock) {
        this.executions = executions; this.rules = rules; this.steps = steps; this.authorization = authorization; this.clock = clock;
    }

    @Transactional
    public void retry(UUID actorId, UUID executionId, AutomationScopeType scopeType, UUID scopeId) {
        if (scopeType == AutomationScopeType.PERSONAL) {
            if (!actorId.equals(scopeId)) throw new AutomationRuleException("That execution is not available.");
        } else authorization.requireLocked(actorId, scopeId, WorkspacePermission.EDIT_SETTINGS);
        var execution = executions.findByIdAndScopeTypeAndScopeId(executionId, scopeType, scopeId)
                .orElseThrow(() -> new AutomationRuleException("That execution is not available."));
        if (!rules.findById(execution.getRuleId()).map(rule -> rule.getStatus() == AutomationRuleStatus.ENABLED).orElse(false))
            throw new AutomationRuleException("Enable the automation before retrying its run.");
        try { execution.manualRetry(clock.instant()); }
        catch (IllegalStateException ex) { throw new AutomationRuleException(ex.getMessage()); }
        steps.findAllByExecutionIdOrderByStepIndexAsc(executionId).forEach(step -> step.prepareRetry());
    }
}
