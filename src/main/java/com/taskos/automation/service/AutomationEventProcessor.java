package com.taskos.automation.service;

import com.taskos.automation.entity.AutomationApprovalRequest;
import com.taskos.automation.entity.AutomationExecution;
import com.taskos.automation.entity.AutomationOutboxEvent;
import com.taskos.automation.entity.AutomationOutboxStatus;
import com.taskos.automation.entity.AutomationRuleStatus;
import com.taskos.automation.entity.AutomationScopeType;
import com.taskos.automation.entity.AutomationStepExecution;
import com.taskos.automation.repository.AutomationApprovalRepository;
import com.taskos.automation.repository.AutomationExecutionRepository;
import com.taskos.automation.repository.AutomationOutboxRepository;
import com.taskos.automation.repository.AutomationRuleRepository;
import com.taskos.automation.repository.AutomationRuleVersionRepository;
import com.taskos.automation.repository.AutomationStepExecutionRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AutomationEventProcessor {
    private final AutomationOutboxRepository outbox;
    private final AutomationRuleRepository rules;
    private final AutomationRuleVersionRepository versions;
    private final AutomationExecutionRepository executions;
    private final AutomationStepExecutionRepository steps;
    private final AutomationApprovalRepository approvals;
    private final AutomationRuleService ruleService;
    private final AutomationConditionEvaluator conditions;
    private final ObjectMapper json;
    private final Clock clock;

    public AutomationEventProcessor(AutomationOutboxRepository outbox, AutomationRuleRepository rules,
            AutomationRuleVersionRepository versions, AutomationExecutionRepository executions,
            AutomationStepExecutionRepository steps, AutomationApprovalRepository approvals,
            AutomationRuleService ruleService, AutomationConditionEvaluator conditions, ObjectMapper json, Clock clock) {
        this.outbox = outbox; this.rules = rules; this.versions = versions; this.executions = executions;
        this.steps = steps; this.approvals = approvals; this.ruleService = ruleService;
        this.conditions = conditions; this.json = json; this.clock = clock;
    }

    @Transactional
    public void process(UUID eventId) {
        AutomationOutboxEvent event = outbox.findById(eventId).orElseThrow();
        if (event.getStatus() != AutomationOutboxStatus.PROCESSING) return;
        if (event.getChainDepth() < 5) {
            Map<String, Object> payload = payload(event.getPayloadJson());
            UUID originatingRuleId = event.getOriginExecutionId() == null ? null :
                    executions.findById(event.getOriginExecutionId()).map(AutomationExecution::getRuleId).orElse(null);
            var matchingRules = rules.findAllByScopeTypeAndScopeIdAndStatusOrderByUpdatedAtDesc(
                    event.getScopeType(), event.getScopeId(), AutomationRuleStatus.ENABLED);
            for (var rule : matchingRules) {
                if (rule.getId().equals(originatingRuleId) || rule.getCurrentVersionId() == null) continue;
                var version = versions.findByIdAndRuleId(rule.getCurrentVersionId(), rule.getId()).orElse(null);
                if (version == null || executions.existsByRuleVersionIdAndTriggerEventId(version.getId(), eventId)) continue;
                var definition = ruleService.read(version.getDefinitionJson());
                if (definition.triggerType() != event.getTriggerType() || !conditions.matches(definition.conditions(), payload)) continue;
                boolean awaitingApproval = definition.requiresApproval() && event.getScopeType() == AutomationScopeType.WORKSPACE;
                AutomationExecution execution = executions.save(AutomationExecution.create(rule.getId(), version.getId(),
                        eventId, event.getScopeType(), event.getScopeId(), event.getActorId(), event.getCorrelationId(),
                        awaitingApproval, clock.instant()));
                for (int index = 0; index < definition.actions().size(); index++) {
                    steps.save(AutomationStepExecution.create(execution.getId(), index,
                            definition.actions().get(index).type(), clock.instant()));
                }
                if (awaitingApproval) approvals.save(AutomationApprovalRequest.create(execution.getId(), event.getScopeId(),
                        event.getActorId() == null ? rule.getCreatedById() : event.getActorId(), clock.instant(),
                        clock.instant().plus(Duration.ofHours(24))));
                rule.recordExecution(clock.instant());
            }
        }
        event.processed(clock.instant());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(String value) {
        try { return (Map<String, Object>) json.readValue(value, Map.class); }
        catch (JacksonException | ClassCastException ex) { throw new AutomationRuleException("An event payload could not be read."); }
    }
}
