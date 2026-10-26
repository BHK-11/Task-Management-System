package com.taskos.automation.service;

import com.taskos.automation.dto.AutomationRuleDefinition;
import com.taskos.automation.dto.AutomationRuleListItem;
import com.taskos.automation.entity.AutomationRule;
import com.taskos.automation.entity.AutomationRuleStatus;
import com.taskos.automation.entity.AutomationRuleVersion;
import com.taskos.automation.entity.AutomationSchedule;
import com.taskos.automation.entity.AutomationScopeType;
import com.taskos.automation.entity.AutomationTriggerType;
import com.taskos.automation.repository.AutomationApprovalRepository;
import com.taskos.automation.repository.AutomationExecutionRepository;
import com.taskos.automation.repository.AutomationRuleRepository;
import com.taskos.automation.repository.AutomationRuleVersionRepository;
import com.taskos.automation.repository.AutomationScheduleRepository;
import com.taskos.collaboration.service.WorkspaceAuthorizationService;
import com.taskos.collaboration.service.WorkspacePermission;
import com.taskos.collaboration.service.WorkspaceTaskService;
import com.taskos.task.entity.Task;
import com.taskos.task.repository.TaskRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AutomationRuleService {
    private final AutomationRuleRepository rules;
    private final AutomationRuleVersionRepository versions;
    private final AutomationExecutionRepository executions;
    private final AutomationApprovalRepository approvals;
    private final AutomationScheduleRepository schedules;
    private final AutomationDefinitionValidator validator;
    private final AutomationScheduleCalculator scheduleCalculator;
    private final AutomationConditionEvaluator evaluator;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceTaskService workspaceTasks;
    private final TaskRepository tasks;
    private final ObjectMapper json;
    private final Clock clock;

    public AutomationRuleService(AutomationRuleRepository rules, AutomationRuleVersionRepository versions,
            AutomationExecutionRepository executions, AutomationApprovalRepository approvals,
            AutomationScheduleRepository schedules, AutomationDefinitionValidator validator,
            AutomationScheduleCalculator scheduleCalculator, AutomationConditionEvaluator evaluator,
            WorkspaceAuthorizationService authorization, WorkspaceTaskService workspaceTasks,
            TaskRepository tasks, ObjectMapper json, Clock clock) {
        this.rules = rules; this.versions = versions; this.executions = executions; this.approvals = approvals;
        this.schedules = schedules; this.validator = validator; this.scheduleCalculator = scheduleCalculator;
        this.evaluator = evaluator; this.authorization = authorization; this.workspaceTasks = workspaceTasks;
        this.tasks = tasks; this.json = json; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<com.taskos.automation.entity.AutomationExecution> history(UUID actor, AutomationScopeType scopeType, UUID scopeId) {
        requireView(actor, scopeType, scopeId);
        return executions.findAllByScopeTypeAndScopeIdOrderByCreatedAtDesc(scopeType, scopeId, PageRequest.of(0, 30));
    }

    @Transactional(readOnly = true)
    public List<AutomationRuleListItem> list(UUID actor, AutomationScopeType scopeType, UUID scopeId) {
        requireView(actor, scopeType, scopeId);
        return rules.findAllByScopeTypeAndScopeIdOrderByUpdatedAtDesc(scopeType, scopeId).stream().map(rule -> {
            AutomationRuleVersion version = rule.getCurrentVersionId() == null ? null :
                    versions.findByIdAndRuleId(rule.getCurrentVersionId(), rule.getId()).orElse(null);
            AutomationTriggerType trigger = version == null ? null : read(version.getDefinitionJson()).triggerType();
            return new AutomationRuleListItem(rule, trigger, version == null ? 0 : version.getVersionNumber());
        }).toList();
    }

    @Transactional(readOnly = true)
    public AutomationRuleDefinition definition(UUID actor, UUID ruleId, AutomationScopeType scopeType, UUID scopeId) {
        requireView(actor, scopeType, scopeId);
        return read(currentVersion(find(ruleId, scopeType, scopeId).getCurrentVersionId(), ruleId).getDefinitionJson());
    }

    @Transactional(readOnly = true)
    public List<com.taskos.automation.entity.AutomationApprovalRequest> pendingApprovals(UUID actor, UUID workspaceId) {
        authorization.require(actor, workspaceId, WorkspacePermission.EDIT_SETTINGS);
        return approvals.findAllByScopeIdAndStatusOrderByCreatedAtAsc(workspaceId,
                com.taskos.automation.entity.AutomationApprovalStatus.PENDING, PageRequest.of(0, 50));
    }

    @Transactional
    public UUID save(UUID actor, UUID ruleId, AutomationScopeType scopeType, UUID scopeId,
            String name, String description, AutomationRuleDefinition definition) {
        requireManage(actor, scopeType, scopeId);
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty() || cleanName.length() > 100) throw new AutomationRuleException("Enter a rule name using 100 characters or fewer.");
        String cleanDescription = description == null ? "" : description.strip();
        if (cleanDescription.length() > 500) throw new AutomationRuleException("Use 500 characters or fewer for the description.");
        validator.validate(definition, scopeType);
        String definitionJson = write(definition);
        if (definitionJson.length() > 12000) throw new AutomationRuleException("This workflow is too large to save.");
        var now = clock.instant();
        AutomationRule rule;
        if (ruleId == null) {
            rule = rules.saveAndFlush(AutomationRule.create(cleanName, cleanDescription, scopeType, scopeId, actor, now));
        } else {
            rule = find(ruleId, scopeType, scopeId);
            if (rule.getStatus() == AutomationRuleStatus.ARCHIVED) throw new AutomationRuleException("Archived rules cannot be edited.");
        }
        int versionNumber = versions.findFirstByRuleIdOrderByVersionNumberDesc(rule.getId())
                .map(existing -> existing.getVersionNumber() + 1).orElse(1);
        AutomationRuleVersion version = versions.saveAndFlush(AutomationRuleVersion.create(rule.getId(), versionNumber,
                definition.schemaVersion(), definitionJson, actor, now));
        rule.publish(version.getId(), cleanName, cleanDescription, now);
        syncSchedule(rule, version, definition);
        return rule.getId();
    }

    @Transactional
    public void enable(UUID actor, UUID ruleId, AutomationScopeType scopeType, UUID scopeId) {
        requireManage(actor, scopeType, scopeId);
        AutomationRule rule = find(ruleId, scopeType, scopeId);
        AutomationRuleVersion version = currentVersion(rule);
        AutomationRuleDefinition definition = read(version.getDefinitionJson());
        validator.validate(definition, scopeType);
        rule.enable(clock.instant());
        syncSchedule(rule, version, definition);
    }

    @Transactional
    public void disable(UUID actor, UUID ruleId, AutomationScopeType scopeType, UUID scopeId) {
        requireManage(actor, scopeType, scopeId);
        AutomationRule rule = find(ruleId, scopeType, scopeId);
        rule.disable(clock.instant());
        schedules.findByRuleId(ruleId).ifPresent(AutomationSchedule::deactivate);
    }

    @Transactional
    public void archive(UUID actor, UUID ruleId, AutomationScopeType scopeType, UUID scopeId) {
        requireManage(actor, scopeType, scopeId);
        AutomationRule rule = find(ruleId, scopeType, scopeId);
        rule.archive(clock.instant());
        schedules.findByRuleId(ruleId).ifPresent(AutomationSchedule::deactivate);
    }

    @Transactional(readOnly = true)
    public boolean dryRun(UUID actor, UUID ruleId, AutomationScopeType scopeType, UUID scopeId, UUID taskId) {
        requireView(actor, scopeType, scopeId);
        AutomationRule rule = find(ruleId, scopeType, scopeId);
        AutomationRuleDefinition definition = read(currentVersion(rule).getDefinitionJson());
        Map<String, Object> values;
        if (scopeType == AutomationScopeType.PERSONAL) {
            Task task = tasks.findDetailByIdAndOwner(taskId, actor).orElseThrow(() -> new AutomationRuleException("Choose one of your own tasks."));
            values = taskValues(task);
        } else {
            Task task = workspaceTasks.task(actor, scopeId, taskId);
            values = taskValues(task);
        }
        return evaluator.matches(definition.conditions(), values);
    }

    @Transactional(readOnly = true)
    public AutomationRule find(UUID id, AutomationScopeType scopeType, UUID scopeId) {
        return rules.findByIdAndScopeTypeAndScopeId(id, scopeType, scopeId)
                .orElseThrow(() -> new AutomationRuleException("That automation is not available."));
    }

    @Transactional(readOnly = true)
    public void assertCanManage(UUID actor, AutomationScopeType scopeType, UUID scopeId) {
        requireManage(actor, scopeType, scopeId);
    }

    @Transactional(readOnly = true)
    public boolean canManage(UUID actor, AutomationScopeType scopeType, UUID scopeId) {
        if (scopeType == AutomationScopeType.PERSONAL) return actor.equals(scopeId);
        try { authorization.require(actor, scopeId, WorkspacePermission.EDIT_SETTINGS); return true; }
        catch (com.taskos.collaboration.service.WorkspaceForbiddenException
                | com.taskos.task.service.TaskNotFoundException ex) { return false; }
    }

    public AutomationRuleVersion currentVersion(AutomationRule rule) {
        if (rule.getCurrentVersionId() == null) throw new AutomationRuleException("Save a rule version before using this automation.");
        return currentVersion(rule.getCurrentVersionId(), rule.getId());
    }

    private AutomationRuleVersion currentVersion(UUID versionId, UUID ruleId) {
        if (versionId == null) throw new AutomationRuleException("Save a rule version before using this automation.");
        return versions.findByIdAndRuleId(versionId, ruleId)
                .orElseThrow(() -> new AutomationRuleException("The current rule version is unavailable."));
    }

    public AutomationRuleDefinition read(String serialized) {
        try { return json.readValue(serialized, AutomationRuleDefinition.class); }
        catch (JacksonException ex) { throw new AutomationRuleException("The saved rule definition could not be read."); }
    }

    private void syncSchedule(AutomationRule rule, AutomationRuleVersion version, AutomationRuleDefinition definition) {
        var scheduleDefinition = definition.schedule();
        var existing = schedules.findByRuleId(rule.getId()).orElse(null);
        if (scheduleDefinition == null) {
            if (existing != null) existing.deactivate();
            return;
        }
        var now = clock.instant();
        var next = scheduleCalculator.next(scheduleDefinition, now.minusNanos(1));
        if (existing == null) {
            AutomationSchedule created = AutomationSchedule.create(rule.getId(), version.getId(),
                    scheduleDefinition.frequency(), scheduleDefinition.dayOfWeek(), scheduleDefinition.localTime(),
                    scheduleDefinition.timezone(), scheduleDefinition.misfirePolicy(), next);
            if (rule.getStatus() != AutomationRuleStatus.ENABLED) created.deactivate();
            schedules.save(created);
        }
        else existing.configure(version.getId(), scheduleDefinition.frequency(), scheduleDefinition.dayOfWeek(),
                scheduleDefinition.localTime(), scheduleDefinition.timezone(), scheduleDefinition.misfirePolicy(), next,
                rule.getStatus() == AutomationRuleStatus.ENABLED);
    }

    private void requireView(UUID actor, AutomationScopeType scopeType, UUID scopeId) {
        if (scopeType == AutomationScopeType.PERSONAL) {
            if (!actor.equals(scopeId)) throw new AutomationRuleException("That personal automation is not available.");
        } else authorization.require(actor, scopeId, WorkspacePermission.VIEW);
    }

    private void requireManage(UUID actor, AutomationScopeType scopeType, UUID scopeId) {
        if (scopeType == AutomationScopeType.PERSONAL) {
            if (!actor.equals(scopeId)) throw new AutomationRuleException("That personal automation is not available.");
        } else authorization.requireLocked(actor, scopeId, WorkspacePermission.EDIT_SETTINGS);
    }

    private Map<String, Object> taskValues(Task task) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("taskId", task.getId()); values.put("title", task.getTitle());
        values.put("status", task.getStatus().name()); values.put("priority", task.getPriority().name());
        values.put("dueDate", task.getDueDate() == null ? null : task.getDueDate().toString());
        values.put("archived", task.getArchivedAt() != null);
        return values;
    }

    private String write(AutomationRuleDefinition definition) {
        try { return json.writeValueAsString(definition); }
        catch (JacksonException ex) { throw new AutomationRuleException("The rule definition could not be saved."); }
    }

}
