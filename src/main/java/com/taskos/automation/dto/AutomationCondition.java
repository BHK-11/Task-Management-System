package com.taskos.automation.dto;

import java.util.List;

public record AutomationCondition(AutomationConditionField field, AutomationComparisonOperator operator,
        List<String> values) { }
