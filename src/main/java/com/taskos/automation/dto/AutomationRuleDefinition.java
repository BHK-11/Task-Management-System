package com.taskos.automation.dto;

import com.taskos.automation.entity.AutomationTriggerType;
import java.util.List;

public record AutomationRuleDefinition(int schemaVersion, AutomationTriggerType triggerType,
        AutomationConditionGroup conditions, List<AutomationActionDefinition> actions,
        boolean requiresApproval, AutomationScheduleDefinition schedule, int maxAttempts) { }
