package com.taskos.automation.dto;

import java.util.List;

public record AutomationConditionGroup(AutomationConditionLogic logic, List<AutomationCondition> conditions,
        List<AutomationConditionGroup> groups) { }
