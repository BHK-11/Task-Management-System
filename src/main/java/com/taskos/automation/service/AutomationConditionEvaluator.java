package com.taskos.automation.service;

import com.taskos.automation.dto.AutomationCondition;
import com.taskos.automation.dto.AutomationConditionField;
import com.taskos.automation.dto.AutomationConditionGroup;
import com.taskos.automation.dto.AutomationComparisonOperator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AutomationConditionEvaluator {
    public boolean matches(AutomationConditionGroup group, Map<String, Object> task) {
        if (group == null) return true;
        List<Boolean> results = new java.util.ArrayList<>();
        if (group.conditions() != null) group.conditions().forEach(condition -> results.add(matches(condition, task)));
        if (group.groups() != null) group.groups().forEach(child -> results.add(matches(child, task)));
        if (results.isEmpty()) return true;
        return group.logic() == com.taskos.automation.dto.AutomationConditionLogic.OR
                ? results.stream().anyMatch(Boolean::booleanValue)
                : results.stream().allMatch(Boolean::booleanValue);
    }

    private boolean matches(AutomationCondition condition, Map<String, Object> task) {
        Object raw = task.get(key(condition.field()));
        String actual = raw == null ? null : raw.toString();
        List<String> values = condition.values() == null ? List.of() : condition.values();
        String expected = values.isEmpty() ? null : values.getFirst();
        return switch (condition.operator()) {
            case IS_EMPTY -> actual == null || actual.isBlank();
            case IS_NOT_EMPTY -> actual != null && !actual.isBlank();
            case EQUALS -> actual != null && actual.equalsIgnoreCase(expected);
            case NOT_EQUALS -> actual != null && !actual.equalsIgnoreCase(expected);
            case CONTAINS -> actual != null && actual.toLowerCase(java.util.Locale.ROOT)
                    .contains(expected.toLowerCase(java.util.Locale.ROOT));
            case IN_LIST -> actual != null && values.stream().anyMatch(value -> actual.equalsIgnoreCase(value));
            case BEFORE_DATE -> actual != null && LocalDate.parse(actual).isBefore(LocalDate.parse(expected));
            case AFTER_DATE -> actual != null && LocalDate.parse(actual).isAfter(LocalDate.parse(expected));
        };
    }

    private String key(AutomationConditionField field) {
        return switch (field) {
            case TASK_STATUS -> "status";
            case TASK_PRIORITY -> "priority";
            case TASK_TITLE -> "title";
            case TASK_DUE_DATE -> "dueDate";
            case TASK_ARCHIVED -> "archived";
        };
    }
}
