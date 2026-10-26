package com.taskos.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.taskos.automation.dto.AutomationActionDefinition;
import com.taskos.automation.dto.AutomationActionType;
import com.taskos.automation.dto.AutomationComparisonOperator;
import com.taskos.automation.dto.AutomationCondition;
import com.taskos.automation.dto.AutomationConditionField;
import com.taskos.automation.dto.AutomationConditionGroup;
import com.taskos.automation.dto.AutomationConditionLogic;
import com.taskos.automation.dto.AutomationRuleDefinition;
import com.taskos.automation.dto.AutomationScheduleDefinition;
import com.taskos.automation.entity.AutomationMisfirePolicy;
import com.taskos.automation.entity.AutomationScheduleFrequency;
import com.taskos.automation.entity.AutomationScopeType;
import com.taskos.automation.entity.AutomationTriggerType;
import com.taskos.automation.service.AutomationConditionEvaluator;
import com.taskos.automation.service.AutomationDefinitionValidator;
import com.taskos.automation.service.AutomationRuleException;
import com.taskos.automation.service.AutomationScheduleCalculator;
import com.taskos.automation.service.AutomationTriggerRegistry;
import com.taskos.task.entity.TaskStatus;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutomationRuleEngineTest {
    private final AutomationConditionEvaluator evaluator = new AutomationConditionEvaluator();
    private final AutomationDefinitionValidator validator = new AutomationDefinitionValidator(new AutomationTriggerRegistry());

    @Test
    void conditionGroupsEvaluateAndAndOrWithTypedComparisons() {
        var status = new AutomationCondition(AutomationConditionField.TASK_STATUS,
                AutomationComparisonOperator.EQUALS, List.of(TaskStatus.IN_PROGRESS.name()));
        var priority = new AutomationCondition(AutomationConditionField.TASK_PRIORITY,
                AutomationComparisonOperator.IN_LIST, List.of("HIGH", "URGENT"));
        Map<String, Object> task = Map.of("status", "IN_PROGRESS", "priority", "HIGH");
        assertTrue(evaluator.matches(new AutomationConditionGroup(AutomationConditionLogic.AND,
                List.of(status, priority), List.of()), task));
        assertTrue(evaluator.matches(new AutomationConditionGroup(AutomationConditionLogic.OR,
                List.of(status, new AutomationCondition(AutomationConditionField.TASK_TITLE,
                        AutomationComparisonOperator.CONTAINS, List.of("release"))), List.of()), task));
        assertFalse(evaluator.matches(new AutomationConditionGroup(AutomationConditionLogic.OR,
                List.of(new AutomationCondition(AutomationConditionField.TASK_STATUS,
                                AutomationComparisonOperator.EQUALS, List.of(TaskStatus.TODO.name())),
                        new AutomationCondition(AutomationConditionField.TASK_TITLE,
                                AutomationComparisonOperator.CONTAINS, List.of("release"))), List.of()), task));
    }

    @Test
    void validatorRejectsUnsupportedComparisonsAndReminderActionsInWorkspaceScope() {
        var invalidStatusCondition = new AutomationCondition(AutomationConditionField.TASK_STATUS,
                AutomationComparisonOperator.CONTAINS, List.of("TODO"));
        var definition = new AutomationRuleDefinition(1, AutomationTriggerType.TASK_CREATED,
                new AutomationConditionGroup(AutomationConditionLogic.AND, List.of(invalidStatusCondition), List.of()),
                List.of(new AutomationActionDefinition(AutomationActionType.CREATE_TASK, Map.of("title", "follow-up"))),
                false, null, 3);
        assertThrows(AutomationRuleException.class, () -> validator.validate(definition, AutomationScopeType.PERSONAL));

        var workspaceReminder = new AutomationRuleDefinition(1, AutomationTriggerType.TASK_CREATED, null,
                List.of(new AutomationActionDefinition(AutomationActionType.CREATE_REMINDER,
                        Map.of("title", "Review", "offsetMinutes", "30"))), false, null, 3);
        assertThrows(AutomationRuleException.class, () -> validator.validate(workspaceReminder, AutomationScopeType.WORKSPACE));
    }

    @Test
    void assignmentActionIsWorkspaceOnlyAndUsesTheTriggeringMember() {
        var action = new AutomationActionDefinition(AutomationActionType.ASSIGN_TASK_TO_TRIGGER_ACTOR, Map.of());
        var workspaceRule = new AutomationRuleDefinition(1, AutomationTriggerType.TASK_CREATED, null,
                List.of(action), false, null, 3);
        assertDoesNotThrow(() -> validator.validate(workspaceRule, AutomationScopeType.WORKSPACE));
        assertThrows(AutomationRuleException.class, () -> validator.validate(workspaceRule, AutomationScopeType.PERSONAL));
    }

    @Test
    void scheduledRulesRequireTaskCreationAndRespectLocalDstWallTime() {
        var invalid = new AutomationRuleDefinition(1, AutomationTriggerType.SCHEDULED, null,
                List.of(new AutomationActionDefinition(AutomationActionType.UPDATE_TASK_STATUS, Map.of("status", "COMPLETED"))),
                false, new AutomationScheduleDefinition(AutomationScheduleFrequency.DAILY, null, LocalTime.of(9, 0),
                        "Asia/Kolkata", AutomationMisfirePolicy.SKIP_MISSED), 3);
        assertThrows(AutomationRuleException.class, () -> validator.validate(invalid, AutomationScopeType.PERSONAL));

        var schedule = new AutomationScheduleDefinition(AutomationScheduleFrequency.DAILY, null, LocalTime.of(2, 30),
                "America/New_York", AutomationMisfirePolicy.RUN_ONCE_AFTER_RECOVERY);
        Instant after = Instant.parse("2026-03-08T06:00:00Z");
        assertTrue(new AutomationScheduleCalculator().next(schedule, after).equals(Instant.parse("2026-03-08T07:30:00Z")));
    }
}
