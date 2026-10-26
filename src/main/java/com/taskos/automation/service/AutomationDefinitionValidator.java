package com.taskos.automation.service;

import com.taskos.automation.dto.AutomationActionDefinition;
import com.taskos.automation.dto.AutomationActionType;
import com.taskos.automation.dto.AutomationCondition;
import com.taskos.automation.dto.AutomationConditionField;
import com.taskos.automation.dto.AutomationConditionGroup;
import com.taskos.automation.dto.AutomationComparisonOperator;
import com.taskos.automation.dto.AutomationRuleDefinition;
import com.taskos.automation.entity.AutomationScheduleFrequency;
import com.taskos.automation.entity.AutomationScopeType;
import com.taskos.automation.entity.AutomationTriggerType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AutomationDefinitionValidator {
    public static final int MAX_ACTIONS = 10;
    private static final int MAX_CONDITIONS = 25;
    private static final int MAX_CONDITION_DEPTH = 4;
    private static final Set<String> TASK_CREATE_KEYS = Set.of("title", "description", "priority", "dueDate");

    private final AutomationTriggerRegistry triggers;

    public AutomationDefinitionValidator(AutomationTriggerRegistry triggers) { this.triggers = triggers; }

    public void validate(AutomationRuleDefinition definition, AutomationScopeType scopeType) {
        if (definition == null || definition.schemaVersion() != 1) fail("Use a supported automation definition version.");
        if (!triggers.supports(definition.triggerType())) fail("Choose a supported trigger.");
        if (definition.actions() == null || definition.actions().isEmpty() || definition.actions().size() > MAX_ACTIONS)
            fail("Add between 1 and 10 supported actions.");
        if (definition.maxAttempts() < 1 || definition.maxAttempts() > 3) fail("Retry attempts must be between 1 and 3.");
        if (definition.requiresApproval() && scopeType != AutomationScopeType.WORKSPACE)
            fail("Approval gates are available only for workspace automations.");
        validateGroup(definition.conditions(), 0, new int[] {0});
        for (AutomationActionDefinition action : definition.actions()) validateAction(action, definition.triggerType(), scopeType);
        if (triggers.isScheduled(definition.triggerType())) validateSchedule(definition);
        else if (definition.schedule() != null) fail("A schedule is only valid for a scheduled trigger.");
    }

    private void validateGroup(AutomationConditionGroup group, int depth, int[] count) {
        if (group == null) return;
        if (group.logic() == null) fail("Choose AND or OR for each condition group.");
        if (depth >= MAX_CONDITION_DEPTH) fail("Condition groups may be nested up to 4 levels.");
        if ((group.conditions() == null || group.conditions().isEmpty()) && (group.groups() == null || group.groups().isEmpty()))
            fail("A condition group cannot be empty.");
        if (group.conditions() != null) for (AutomationCondition condition : group.conditions()) {
            if (++count[0] > MAX_CONDITIONS) fail("Use 25 conditions or fewer.");
            validateCondition(condition);
        }
        if (group.groups() != null) for (AutomationConditionGroup child : group.groups()) validateGroup(child, depth + 1, count);
    }

    private void validateCondition(AutomationCondition condition) {
        if (condition == null || condition.field() == null || condition.operator() == null) fail("Choose a valid condition field and comparison.");
        List<String> values = condition.values() == null ? List.of() : condition.values();
        boolean unary = condition.operator() == AutomationComparisonOperator.IS_EMPTY
                || condition.operator() == AutomationComparisonOperator.IS_NOT_EMPTY;
        if (unary && !values.isEmpty()) fail("Empty checks do not take a value.");
        if (!unary && condition.operator() == AutomationComparisonOperator.IN_LIST && (values.size() < 2 || values.size() > 10))
            fail("List comparisons need between 2 and 10 values.");
        if (!unary && condition.operator() != AutomationComparisonOperator.IN_LIST && values.size() != 1)
            fail("This comparison needs exactly one value.");
        Set<AutomationComparisonOperator> supported = switch (condition.field()) {
            case TASK_STATUS, TASK_PRIORITY -> EnumSet.of(AutomationComparisonOperator.EQUALS,
                    AutomationComparisonOperator.NOT_EQUALS, AutomationComparisonOperator.IN_LIST);
            case TASK_TITLE -> EnumSet.of(AutomationComparisonOperator.EQUALS, AutomationComparisonOperator.NOT_EQUALS,
                    AutomationComparisonOperator.CONTAINS, AutomationComparisonOperator.IS_EMPTY, AutomationComparisonOperator.IS_NOT_EMPTY);
            case TASK_DUE_DATE -> EnumSet.of(AutomationComparisonOperator.EQUALS, AutomationComparisonOperator.NOT_EQUALS,
                    AutomationComparisonOperator.BEFORE_DATE, AutomationComparisonOperator.AFTER_DATE,
                    AutomationComparisonOperator.IS_EMPTY, AutomationComparisonOperator.IS_NOT_EMPTY);
            case TASK_ARCHIVED -> EnumSet.of(AutomationComparisonOperator.EQUALS, AutomationComparisonOperator.NOT_EQUALS);
        };
        if (!supported.contains(condition.operator())) fail("That comparison is not available for this field.");
        for (String value : values) if (value == null || value.length() > 160) fail("Condition values must be 160 characters or fewer.");
        if (condition.field() == AutomationConditionField.TASK_STATUS)
            values.forEach(value -> enumValue(value, com.taskos.task.entity.TaskStatus.class, "Choose a valid task status."));
        if (condition.field() == AutomationConditionField.TASK_PRIORITY)
            values.forEach(value -> enumValue(value, com.taskos.task.entity.TaskPriority.class, "Choose a valid task priority."));
        if (condition.field() == AutomationConditionField.TASK_ARCHIVED)
            values.forEach(value -> { if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) fail("Archived state must be true or false."); });
        if (condition.field() == AutomationConditionField.TASK_DUE_DATE
                && condition.operator() != AutomationComparisonOperator.IS_EMPTY
                && condition.operator() != AutomationComparisonOperator.IS_NOT_EMPTY)
            values.forEach(value -> parseDate(value));
    }

    private void validateAction(AutomationActionDefinition action, AutomationTriggerType trigger, AutomationScopeType scopeType) {
        if (action == null || action.type() == null) fail("Choose a supported action.");
        Map<String, String> parameters = action.parameters() == null ? Map.of() : action.parameters();
        switch (action.type()) {
            case CREATE_TASK -> {
                onlyKeys(parameters, TASK_CREATE_KEYS);
                required(parameters, "title", 160);
                if (parameters.getOrDefault("description", "").length() > 6000) fail("Task description must be 6000 characters or fewer.");
                if (parameters.containsKey("priority")) enumValue(parameters.get("priority"), com.taskos.task.entity.TaskPriority.class, "Choose a valid task priority.");
                if (parameters.containsKey("dueDate") && !parameters.get("dueDate").isBlank()) parseDate(parameters.get("dueDate"));
            }
            case UPDATE_TASK_STATUS -> {
                onlyKeys(parameters, Set.of("status")); required(parameters, "status", 32);
                enumValue(parameters.get("status"), com.taskos.task.entity.TaskStatus.class, "Choose a valid task status.");
                if (trigger == AutomationTriggerType.SCHEDULED) fail("A scheduled rule must create a task because no source task is available.");
            }
            case SET_TASK_PRIORITY -> {
                onlyKeys(parameters, Set.of("priority")); required(parameters, "priority", 16);
                enumValue(parameters.get("priority"), com.taskos.task.entity.TaskPriority.class, "Choose a valid task priority.");
                if (trigger == AutomationTriggerType.SCHEDULED) fail("A scheduled rule must create a task because no source task is available.");
            }
            case CREATE_REMINDER -> {
                if (scopeType == AutomationScopeType.WORKSPACE) fail("Reminder actions are available only for personal rules.");
                onlyKeys(parameters, Set.of("title", "offsetMinutes")); required(parameters, "title", 160);
                int offset;
                try { offset = Integer.parseInt(parameters.getOrDefault("offsetMinutes", "")); }
                catch (NumberFormatException ex) { throw new AutomationRuleException("Reminder offset must be a whole number of minutes."); }
                if (offset < 1 || offset > 525600) fail("Reminder offset must be within one year.");
                if (trigger == AutomationTriggerType.SCHEDULED) fail("A scheduled rule must create a task because no source task is available.");
            }
            case ASSIGN_TASK_TO_TRIGGER_ACTOR -> {
                onlyKeys(parameters, Set.of());
                if (scopeType != AutomationScopeType.WORKSPACE) fail("Assignment actions are available only for workspace rules.");
                if (trigger == AutomationTriggerType.SCHEDULED) fail("A scheduled rule has no triggering member to assign the task to.");
            }
        }
    }

    private void validateSchedule(AutomationRuleDefinition definition) {
        var schedule = definition.schedule();
        if (schedule == null || schedule.frequency() == null || schedule.localTime() == null) fail("Choose a schedule frequency and time.");
        try { ZoneId.of(schedule.timezone()); } catch (RuntimeException ex) { fail("Choose a valid schedule timezone."); }
        if (schedule.frequency() == AutomationScheduleFrequency.WEEKLY && schedule.dayOfWeek() == null)
            fail("Choose a weekday for a weekly schedule.");
        if (schedule.frequency() == AutomationScheduleFrequency.DAILY && schedule.dayOfWeek() != null)
            fail("Daily schedules do not use a weekday.");
        if (schedule.misfirePolicy() == null) fail("Choose a schedule recovery policy.");
        if (definition.actions().stream().anyMatch(action -> action.type() != AutomationActionType.CREATE_TASK))
            fail("Scheduled automations currently support task-creation actions only.");
    }

    private void onlyKeys(Map<String, String> parameters, Set<String> allowed) {
        if (!allowed.containsAll(parameters.keySet())) fail("This action contains an unsupported parameter.");
    }
    private void required(Map<String, String> parameters, String key, int maxLength) {
        String value = parameters.get(key);
        if (value == null || value.isBlank() || value.strip().length() > maxLength) fail("Enter a valid " + key + ".");
    }
    private void parseDate(String value) {
        try { LocalDate.parse(value); } catch (RuntimeException ex) { fail("Enter a date in YYYY-MM-DD format."); }
    }
    private <E extends Enum<E>> void enumValue(String value, Class<E> type, String error) {
        try { Enum.valueOf(type, value); } catch (RuntimeException ex) { fail(error); }
    }
    private void fail(String message) { throw new AutomationRuleException(message); }
}
