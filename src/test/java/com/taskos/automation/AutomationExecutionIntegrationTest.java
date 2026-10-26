package com.taskos.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.taskos.automation.dto.AutomationActionDefinition;
import com.taskos.automation.dto.AutomationActionType;
import com.taskos.automation.dto.AutomationComparisonOperator;
import com.taskos.automation.dto.AutomationCondition;
import com.taskos.automation.dto.AutomationConditionField;
import com.taskos.automation.dto.AutomationConditionGroup;
import com.taskos.automation.dto.AutomationConditionLogic;
import com.taskos.automation.dto.AutomationRuleDefinition;
import com.taskos.automation.entity.AutomationExecutionStatus;
import com.taskos.automation.entity.AutomationScopeType;
import com.taskos.automation.entity.AutomationStepStatus;
import com.taskos.automation.entity.AutomationTriggerType;
import com.taskos.automation.repository.AutomationExecutionRepository;
import com.taskos.automation.repository.AutomationStepExecutionRepository;
import com.taskos.automation.service.AutomationRuleService;
import com.taskos.auth.service.RegistrationService;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.service.TaskService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AutomationExecutionIntegrationTest {
    @Autowired private RegistrationService registration;
    @Autowired private TaskService tasks;
    @Autowired private AutomationRuleService rules;
    @Autowired private AutomationExecutionRepository executions;
    @Autowired private AutomationStepExecutionRepository steps;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void outboxKeepsEventsWithoutBlockingActorDeletion() {
        var deleteRule = jdbc.queryForList("""
                select rc.constraint_name || ':' || rc.delete_rule from information_schema.referential_constraints rc
                join information_schema.table_constraints tc
                  on tc.constraint_schema = rc.constraint_schema and tc.constraint_name = rc.constraint_name
                join information_schema.key_column_usage kcu
                  on kcu.constraint_schema = tc.constraint_schema and kcu.constraint_name = tc.constraint_name
                where tc.table_schema = 'task_os' and tc.table_name = 'automation_outbox'
                  and tc.constraint_type = 'FOREIGN KEY' and kcu.column_name = 'actor_id'
                """, String.class);
        assertEquals(List.of("fk_automation_outbox_actor:SET NULL"), deleteRule,
                () -> "automation_outbox actor FK delete rules: " + deleteRule);
    }

    @Test
    void taskEventRunsMatchingVersionedWorkflowThroughTaskService() throws Exception {
        UUID owner = registration.register("Automation Owner", "automation-owner-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase").getId();
        TaskForm task = new TaskForm(); task.setTitle("Regression task"); task.setPriority(TaskPriority.MEDIUM);
        UUID taskId = tasks.create(owner, task);

        var condition = new AutomationCondition(AutomationConditionField.TASK_PRIORITY,
                AutomationComparisonOperator.EQUALS, List.of(TaskPriority.LOW.name()));
        var definition = new AutomationRuleDefinition(1, AutomationTriggerType.TASK_PRIORITY_CHANGED,
                new AutomationConditionGroup(AutomationConditionLogic.AND, List.of(condition), List.of()),
                List.of(new AutomationActionDefinition(AutomationActionType.UPDATE_TASK_STATUS,
                        Map.of("status", TaskStatus.COMPLETED.name()))), false, null, 3);
        UUID ruleId = rules.save(owner, null, AutomationScopeType.PERSONAL, owner,
                "Complete low-priority work", "A test workflow", definition);
        rules.enable(owner, ruleId, AutomationScopeType.PERSONAL, owner);

        var current = tasks.detail(owner, taskId).task();
        TaskForm update = new TaskForm(); update.setTitle(current.getTitle()); update.setDescription(current.getDescription());
        update.setStatus(current.getStatus()); update.setPriority(TaskPriority.LOW); update.setStartDate(current.getStartDate());
        update.setDueDate(current.getDueDate()); update.setVersion(current.getVersion());
        tasks.update(owner, taskId, update);

        var deadline = System.nanoTime() + java.time.Duration.ofSeconds(12).toNanos();
        while (System.nanoTime() < deadline) {
            var finished = executions.findAllByRuleIdOrderByCreatedAtDesc(ruleId, org.springframework.data.domain.PageRequest.of(0, 1));
            if (!finished.isEmpty() && finished.getFirst().getStatus() == AutomationExecutionStatus.SUCCEEDED) break;
            Thread.sleep(100);
        }
        var history = executions.findAllByRuleIdOrderByCreatedAtDesc(ruleId, org.springframework.data.domain.PageRequest.of(0, 1));
        assertEquals(1, history.size());
        assertEquals(AutomationExecutionStatus.SUCCEEDED, history.getFirst().getStatus());
        assertEquals(TaskStatus.COMPLETED, tasks.detail(owner, taskId).task().getStatus());
        var stepHistory = steps.findAllByExecutionIdOrderByStepIndexAsc(history.getFirst().getId());
        assertEquals(1, stepHistory.size()); assertEquals(AutomationStepStatus.SUCCEEDED, stepHistory.getFirst().getStatus());
        assertNotNull(history.getFirst().getRuleVersionId());
        assertTrue(history.getFirst().getCompletedAt().isAfter(history.getFirst().getCreatedAt()));
    }
}
