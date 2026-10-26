package com.taskos.automation.dto;

import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class AutomationRuleForm {
    @Size(max = 100) private String name = "";
    @Size(max = 500) private String description = "";
    private String scopeType = "PERSONAL";
    private String scopeId = "";
    private String triggerType = "TASK_CREATED";
    private String conditionLogic = "AND";
    private List<String> conditionFields = new ArrayList<>(List.of("", "", ""));
    private List<String> conditionOperators = new ArrayList<>(List.of("EQUALS", "EQUALS", "EQUALS"));
    private List<String> conditionValues = new ArrayList<>(List.of("", "", ""));
    private List<String> actionTypes = new ArrayList<>(List.of("CREATE_TASK", "", ""));
    private List<String> actionTitles = new ArrayList<>(List.of("", "", ""));
    private List<String> actionDescriptions = new ArrayList<>(List.of("", "", ""));
    private List<String> actionStatuses = new ArrayList<>(List.of("IN_PROGRESS", "IN_PROGRESS", "IN_PROGRESS"));
    private List<String> actionPriorities = new ArrayList<>(List.of("MEDIUM", "MEDIUM", "MEDIUM"));
    private List<String> actionDueDates = new ArrayList<>(List.of("", "", ""));
    private List<String> reminderOffsets = new ArrayList<>(List.of("60", "60", "60"));
    private boolean requiresApproval;
    private int maxAttempts = 3;
    private String scheduleFrequency = "DAILY";
    private String scheduleDay = "MONDAY";
    private String scheduleTime = "09:00";
    private String scheduleTimezone = "Asia/Kolkata";
    private String misfirePolicy = "SKIP_MISSED";

    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String value) { scopeType = value; }
    public String getScopeId() { return scopeId; }
    public void setScopeId(String value) { scopeId = value; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String value) { triggerType = value; }
    public String getConditionLogic() { return conditionLogic; }
    public void setConditionLogic(String value) { conditionLogic = value; }
    public List<String> getConditionFields() { return conditionFields; }
    public void setConditionFields(List<String> value) { conditionFields = resize(value, ""); }
    public List<String> getConditionOperators() { return conditionOperators; }
    public void setConditionOperators(List<String> value) { conditionOperators = resize(value, "EQUALS"); }
    public List<String> getConditionValues() { return conditionValues; }
    public void setConditionValues(List<String> value) { conditionValues = resize(value, ""); }
    public List<String> getActionTypes() { return actionTypes; }
    public void setActionTypes(List<String> value) { actionTypes = resize(value, ""); }
    public List<String> getActionTitles() { return actionTitles; }
    public void setActionTitles(List<String> value) { actionTitles = resize(value, ""); }
    public List<String> getActionDescriptions() { return actionDescriptions; }
    public void setActionDescriptions(List<String> value) { actionDescriptions = resize(value, ""); }
    public List<String> getActionStatuses() { return actionStatuses; }
    public void setActionStatuses(List<String> value) { actionStatuses = resize(value, "IN_PROGRESS"); }
    public List<String> getActionPriorities() { return actionPriorities; }
    public void setActionPriorities(List<String> value) { actionPriorities = resize(value, "MEDIUM"); }
    public List<String> getActionDueDates() { return actionDueDates; }
    public void setActionDueDates(List<String> value) { actionDueDates = resize(value, ""); }
    public List<String> getReminderOffsets() { return reminderOffsets; }
    public void setReminderOffsets(List<String> value) { reminderOffsets = resize(value, "60"); }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean value) { requiresApproval = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { maxAttempts = value; }
    public String getScheduleFrequency() { return scheduleFrequency; }
    public void setScheduleFrequency(String value) { scheduleFrequency = value; }
    public String getScheduleDay() { return scheduleDay; }
    public void setScheduleDay(String value) { scheduleDay = value; }
    public String getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(String value) { scheduleTime = value; }
    public String getScheduleTimezone() { return scheduleTimezone; }
    public void setScheduleTimezone(String value) { scheduleTimezone = value; }
    public String getMisfirePolicy() { return misfirePolicy; }
    public void setMisfirePolicy(String value) { misfirePolicy = value; }

    private static List<String> resize(List<String> values, String defaultValue) {
        List<String> result = new ArrayList<>(values == null ? List.of() : values.stream().limit(3).toList());
        while (result.size() < 3) result.add(defaultValue);
        return result;
    }
}
