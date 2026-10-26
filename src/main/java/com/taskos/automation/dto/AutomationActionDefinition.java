package com.taskos.automation.dto;

import java.util.Map;

public record AutomationActionDefinition(AutomationActionType type, Map<String, String> parameters) { }
