package com.taskos.automation.dto;

import com.taskos.automation.entity.AutomationRule;
import com.taskos.automation.entity.AutomationTriggerType;

public record AutomationRuleListItem(AutomationRule rule, AutomationTriggerType trigger, int versionNumber) { }
