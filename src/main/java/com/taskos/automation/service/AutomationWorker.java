package com.taskos.automation.service;

import com.taskos.automation.dto.AutomationActionDefinition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationWorker {
    private final AutomationOutboxService outbox;
    private final AutomationEventProcessor events;
    private final AutomationExecutionCoordinator executions;
    private final AutomationActionStepService actions;
    private final AutomationApprovalService approvals;
    private final AutomationScheduleService schedules;

    public AutomationWorker(AutomationOutboxService outbox, AutomationEventProcessor events,
            AutomationExecutionCoordinator executions, AutomationActionStepService actions,
            AutomationApprovalService approvals, AutomationScheduleService schedules) {
        this.outbox = outbox; this.events = events; this.executions = executions;
        this.actions = actions; this.approvals = approvals; this.schedules = schedules;
    }

    @Scheduled(fixedDelay = 1_000)
    public void drainOutbox() {
        for (int count = 0; count < 20; count++) {
            Optional<UUID> event = outbox.claimNext();
            if (event.isEmpty()) return;
            try { events.process(event.get()); }
            catch (RuntimeException ex) { outbox.recordFailure(event.get()); }
        }
    }

    @Scheduled(fixedDelay = 1_000)
    public void runWorkflows() {
        for (int count = 0; count < 10; count++) {
            Optional<UUID> claimed = executions.claimNext();
            if (claimed.isEmpty()) return;
            run(claimed.get());
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void runSchedulesAndExpiry() {
        schedules.fireDue(); approvals.expirePending();
    }

    private void run(UUID id) {
        AutomationExecutionCoordinator.Workflow workflow;
        try { workflow = executions.workflow(id); }
        catch (RuntimeException ex) { executions.fail(id, 3, ex); return; }
        if (workflow.ruleStatus() != com.taskos.automation.entity.AutomationRuleStatus.ENABLED) {
            executions.cancelIfDisabled(id); return;
        }
        var definitions = workflow.definition().actions();
        for (int index = 0; index < definitions.size(); index++) {
            AutomationActionDefinition action = definitions.get(index);
            try { actions.execute(id, index, action); }
            catch (RuntimeException ex) {
                executions.failStep(id, index, action.type(), workflow.definition().maxAttempts(), ex);
                executions.fail(id, workflow.definition().maxAttempts(), ex);
                return;
            }
        }
        executions.succeed(id);
    }
}
