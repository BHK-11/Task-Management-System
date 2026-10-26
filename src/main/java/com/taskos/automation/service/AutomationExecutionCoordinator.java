package com.taskos.automation.service;

import com.taskos.automation.entity.AutomationExecution;
import com.taskos.automation.entity.AutomationExecutionStatus;
import com.taskos.automation.entity.AutomationRuleStatus;
import com.taskos.automation.entity.AutomationStepExecution;
import com.taskos.automation.entity.AutomationStepStatus;
import com.taskos.automation.repository.AutomationExecutionRepository;
import com.taskos.automation.repository.AutomationOutboxRepository;
import com.taskos.automation.repository.AutomationRuleRepository;
import com.taskos.automation.repository.AutomationRuleVersionRepository;
import com.taskos.automation.repository.AutomationStepExecutionRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationExecutionCoordinator {
    private final AutomationExecutionRepository executions;
    private final AutomationRuleRepository rules;
    private final AutomationRuleVersionRepository versions;
    private final AutomationOutboxRepository outbox;
    private final AutomationStepExecutionRepository steps;
    private final AutomationRuleService ruleService;
    private final Clock clock;

    public AutomationExecutionCoordinator(AutomationExecutionRepository executions, AutomationRuleRepository rules,
            AutomationRuleVersionRepository versions, AutomationOutboxRepository outbox,
            AutomationStepExecutionRepository steps, AutomationRuleService ruleService, Clock clock) {
        this.executions = executions; this.rules = rules; this.versions = versions; this.outbox = outbox;
        this.steps = steps; this.ruleService = ruleService; this.clock = clock;
    }

    @Transactional
    public Optional<UUID> claimNext() {
        var due = executions.lockRunnable(List.of(AutomationExecutionStatus.PENDING, AutomationExecutionStatus.FAILED_RETRYABLE),
                AutomationExecutionStatus.RUNNING, clock.instant(), PageRequest.of(0, 1));
        if (due.isEmpty()) return Optional.empty();
        var execution = due.getFirst();
        execution.claim(clock.instant(), clock.instant().plus(Duration.ofMinutes(2)));
        return Optional.of(execution.getId());
    }

    @Transactional(readOnly = true)
    public Workflow workflow(UUID id) {
        var execution = executions.findById(id).orElseThrow();
        var rule = rules.findById(execution.getRuleId()).orElseThrow();
        var version = versions.findByIdAndRuleId(execution.getRuleVersionId(), rule.getId()).orElseThrow();
        var event = outbox.findById(execution.getTriggerEventId()).orElseThrow();
        return new Workflow(execution.getId(), rule.getId(), rule.getCreatedById(), rule.getStatus(),
                ruleService.read(version.getDefinitionJson()), event.getId(), event.getChainDepth());
    }

    @Transactional
    public void cancelIfDisabled(UUID id) {
        executions.findById(id).filter(execution -> execution.getStatus() == AutomationExecutionStatus.RUNNING)
                .ifPresent(execution -> {
                    if (rules.findById(execution.getRuleId()).map(rule -> rule.getStatus() != AutomationRuleStatus.ENABLED).orElse(true))
                        execution.cancel(clock.instant());
                });
    }

    @Transactional
    public void succeed(UUID id) {
        executions.findById(id).filter(execution -> execution.getStatus() == AutomationExecutionStatus.RUNNING)
                .ifPresent(execution -> execution.succeed(clock.instant()));
    }

    @Transactional
    public void fail(UUID id, int maxAttempts, RuntimeException failure) {
        executions.findById(id).filter(execution -> execution.getStatus() == AutomationExecutionStatus.RUNNING)
                .ifPresent(execution -> {
                    var now = clock.instant();
                    String code = permanent(failure) ? "ACTION_REJECTED" : "ACTION_RETRYABLE";
                    String summary = permanent(failure) ? "A workflow action could not be applied." : "A temporary action error occurred.";
                    boolean permanent = permanent(failure);
                    if (permanent) execution.retry(now, now, code, summary, true);
                    else if (execution.getAttemptCount() + 1 >= maxAttempts) execution.deadLetter(now, code, summary);
                    else {
                        long[] delays = {10, 30, 90};
                        long seconds = delays[Math.min(execution.getAttemptCount(), delays.length - 1)];
                        execution.retry(now, now.plusSeconds(seconds), code, summary, false);
                    }
                });
    }

    @Transactional
    public void failStep(UUID id, int index, com.taskos.automation.dto.AutomationActionType type,
            int maxAttempts, RuntimeException failure) {
        executions.findById(id).ifPresent(execution -> {
            var now = clock.instant();
            boolean permanent = permanent(failure);
            boolean finalAttempt = execution.getAttemptCount() + 1 >= maxAttempts;
            var step = steps.findByExecutionIdAndStepIndex(id, index).orElseGet(() ->
                    steps.save(AutomationStepExecution.create(id, index, type, now)));
            if (step.getStatus() == AutomationStepStatus.SUCCEEDED) return;
            step.start(now); step.fail(now, permanent || finalAttempt ? "ACTION_FAILED" : "ACTION_RETRYABLE", permanent || finalAttempt);
        });
    }

    private boolean permanent(RuntimeException failure) {
        return failure instanceof AutomationRuleException
                || failure instanceof com.taskos.task.service.TaskRuleException
                || failure instanceof com.taskos.collaboration.service.WorkspaceForbiddenException
                || failure instanceof com.taskos.time.service.TimeRuleException
                || failure instanceof com.taskos.task.service.TaskNotFoundException
                || failure instanceof IllegalArgumentException;
    }

    public record Workflow(UUID executionId, UUID ruleId, UUID ruleCreatorId, AutomationRuleStatus ruleStatus,
            com.taskos.automation.dto.AutomationRuleDefinition definition, UUID eventId, int chainDepth) { }
}
