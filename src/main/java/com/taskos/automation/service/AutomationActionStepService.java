package com.taskos.automation.service;

import com.taskos.automation.dto.AutomationActionDefinition;
import com.taskos.automation.dto.AutomationActionType;
import com.taskos.automation.entity.AutomationExecution;
import com.taskos.automation.entity.AutomationRuleStatus;
import com.taskos.automation.entity.AutomationScopeType;
import com.taskos.automation.entity.AutomationStepExecution;
import com.taskos.automation.entity.AutomationStepStatus;
import com.taskos.automation.repository.AutomationExecutionRepository;
import com.taskos.automation.repository.AutomationOutboxRepository;
import com.taskos.automation.repository.AutomationRuleRepository;
import com.taskos.automation.repository.AutomationStepExecutionRepository;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.service.TaskService;
import com.taskos.collaboration.service.WorkspaceTaskService;
import com.taskos.time.service.ReminderService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationActionStepService {
    private final AutomationExecutionRepository executions;
    private final AutomationRuleRepository rules;
    private final AutomationOutboxRepository outbox;
    private final AutomationStepExecutionRepository steps;
    private final TaskService tasks;
    private final WorkspaceTaskService workspaceTasks;
    private final ReminderService reminders;
    private final Clock clock;

    public AutomationActionStepService(AutomationExecutionRepository executions, AutomationRuleRepository rules,
            AutomationOutboxRepository outbox, AutomationStepExecutionRepository steps, TaskService tasks,
            WorkspaceTaskService workspaceTasks, ReminderService reminders, Clock clock) {
        this.executions = executions; this.rules = rules; this.outbox = outbox;
        this.steps = steps; this.tasks = tasks; this.workspaceTasks = workspaceTasks;
        this.reminders = reminders; this.clock = clock;
    }

    @Transactional
    public void execute(UUID executionId, int index, AutomationActionDefinition action) {
        AutomationExecution execution = executions.findById(executionId).orElseThrow();
        var step = steps.findByExecutionIdAndStepIndex(executionId, index).orElseThrow();
        if (step.getStatus() == AutomationStepStatus.SUCCEEDED) return;
        if (step.getStatus() == AutomationStepStatus.FAILED_PERMANENT)
            throw new AutomationRuleException("This action has a permanent failure.");
        var rule = rules.findById(execution.getRuleId()).orElseThrow();
        if (rule.getStatus() != AutomationRuleStatus.ENABLED)
            throw new AutomationRuleException("This automation is no longer enabled.");
        var event = outbox.findById(execution.getTriggerEventId()).orElseThrow();
        UUID actor = execution.getScopeType() == AutomationScopeType.PERSONAL ? execution.getScopeId() : rule.getCreatedById();
        UUID taskId = event.getEntityId();
        step.start(clock.instant());
        String summary = switch (action.type()) {
            case CREATE_TASK -> createTask(execution, event, action, actor);
            case UPDATE_TASK_STATUS -> updateStatus(execution, event, action, actor, taskId);
            case SET_TASK_PRIORITY -> setPriority(execution, event, action, actor, taskId);
            case CREATE_REMINDER -> createReminder(execution, event, action, actor, taskId);
            case ASSIGN_TASK_TO_TRIGGER_ACTOR -> assignToTriggerActor(execution, event, actor, taskId);
        };
        step.succeed(clock.instant(), summary);
    }

    private String createTask(AutomationExecution execution, com.taskos.automation.entity.AutomationOutboxEvent event,
            AutomationActionDefinition action, UUID actor) {
        var parameters = action.parameters();
        TaskForm form = new TaskForm();
        form.setTitle(parameters.get("title")); form.setDescription(parameters.getOrDefault("description", ""));
        form.setStatus(TaskStatus.TODO);
        form.setPriority(parse(parameters.get("priority"), TaskPriority.class, TaskPriority.MEDIUM));
        if (parameters.get("dueDate") != null && !parameters.get("dueDate").isBlank()) form.setDueDate(LocalDate.parse(parameters.get("dueDate")));
        int depth = event.getChainDepth() + 1;
        if (execution.getScopeType() == AutomationScopeType.WORKSPACE)
            workspaceTasks.createFromAutomation(actor, execution.getScopeId(), form.getTitle(), form.getDescription(),
                    form.getPriority(), form.getDueDate(), execution.getId(), event.getId(), depth);
        else tasks.createFromAutomation(actor, form, execution.getId(), event.getId(), depth);
        return "Task created";
    }

    private String updateStatus(AutomationExecution execution, com.taskos.automation.entity.AutomationOutboxEvent event,
            AutomationActionDefinition action, UUID actor, UUID taskId) {
        requireSourceTask(taskId);
        TaskStatus status = parse(action.parameters().get("status"), TaskStatus.class, null);
        int depth = event.getChainDepth() + 1;
        if (execution.getScopeType() == AutomationScopeType.WORKSPACE)
            workspaceTasks.changeStatusFromAutomation(actor, execution.getScopeId(), taskId, status, execution.getId(), event.getId(), depth);
        else tasks.transitionFromAutomation(actor, taskId, status, execution.getId(), event.getId(), depth);
        return "Status set to " + status.name();
    }

    private String setPriority(AutomationExecution execution, com.taskos.automation.entity.AutomationOutboxEvent event,
            AutomationActionDefinition action, UUID actor, UUID taskId) {
        requireSourceTask(taskId);
        TaskPriority priority = parse(action.parameters().get("priority"), TaskPriority.class, null);
        int depth = event.getChainDepth() + 1;
        if (execution.getScopeType() == AutomationScopeType.WORKSPACE)
            workspaceTasks.setPriorityFromAutomation(actor, execution.getScopeId(), taskId, priority, execution.getId(), event.getId(), depth);
        else tasks.setPriorityFromAutomation(actor, taskId, priority, execution.getId(), event.getId(), depth);
        return "Priority set to " + priority.name();
    }

    private String createReminder(AutomationExecution execution, com.taskos.automation.entity.AutomationOutboxEvent event,
            AutomationActionDefinition action, UUID actor, UUID taskId) {
        requireSourceTask(taskId);
        if (execution.getScopeType() != AutomationScopeType.PERSONAL) throw new AutomationRuleException("Reminder actions are personal only.");
        reminders.createFromAutomation(actor, taskId, action.parameters().get("title"),
                Integer.parseInt(action.parameters().get("offsetMinutes")));
        return "In-app reminder scheduled";
    }

    private String assignToTriggerActor(AutomationExecution execution,
            com.taskos.automation.entity.AutomationOutboxEvent event, UUID actor, UUID taskId) {
        requireSourceTask(taskId);
        if (execution.getScopeType() != AutomationScopeType.WORKSPACE || event.getActorId() == null)
            throw new AutomationRuleException("This action needs a workspace task event with a member actor.");
        workspaceTasks.assignFromAutomation(actor, execution.getScopeId(), taskId, event.getActorId(),
                execution.getId(), event.getId(), event.getChainDepth() + 1);
        return "Task assigned to the member who triggered the event";
    }

    private void requireSourceTask(UUID taskId) {
        if (taskId == null) throw new AutomationRuleException("This action needs a task trigger.");
    }

    private <E extends Enum<E>> E parse(String value, Class<E> type, E defaultValue) {
        if ((value == null || value.isBlank()) && defaultValue != null) return defaultValue;
        try { return Enum.valueOf(type, value); }
        catch (RuntimeException ex) { throw new AutomationRuleException("An action parameter is invalid."); }
    }
}
