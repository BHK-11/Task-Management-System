package com.taskos.automation.service;

import com.taskos.automation.entity.AutomationTriggerType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AutomationTriggerRegistry {
    public List<AutomationTriggerType> supported() {
        return List.of(AutomationTriggerType.TASK_CREATED, AutomationTriggerType.TASK_STATUS_CHANGED,
                AutomationTriggerType.TASK_COMPLETED, AutomationTriggerType.TASK_REOPENED,
                AutomationTriggerType.TASK_PRIORITY_CHANGED, AutomationTriggerType.TASK_DUE_DATE_CHANGED,
                AutomationTriggerType.TASK_TAG_ADDED, AutomationTriggerType.TASK_ASSIGNED, AutomationTriggerType.SCHEDULED);
    }
    public boolean supports(AutomationTriggerType trigger) { return trigger != null && supported().contains(trigger); }
    public boolean isScheduled(AutomationTriggerType trigger) { return trigger == AutomationTriggerType.SCHEDULED; }
}
