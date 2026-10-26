package com.taskos.automation.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.automation.dto.*;
import com.taskos.automation.entity.*;
import com.taskos.automation.repository.AutomationExecutionRepository;
import com.taskos.automation.repository.AutomationRuleRepository;
import com.taskos.automation.repository.AutomationRuleVersionRepository;
import com.taskos.automation.repository.AutomationStepExecutionRepository;
import com.taskos.automation.service.AutomationApprovalService;
import com.taskos.automation.service.AutomationRuleException;
import com.taskos.automation.service.AutomationRuleService;
import com.taskos.automation.service.AutomationRetryService;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/automations")
public class AutomationController {
    private final AutomationRuleService service;
    private final AutomationApprovalService approvals;
    private final AutomationExecutionRepository executions;
    private final AutomationRuleRepository rules;
    private final AutomationRuleVersionRepository versions;
    private final AutomationStepExecutionRepository steps;
    private final AutomationRetryService retryService;

    public AutomationController(AutomationRuleService service, AutomationApprovalService approvals,
            AutomationExecutionRepository executions, AutomationRuleRepository rules,
            AutomationRuleVersionRepository versions, AutomationStepExecutionRepository steps,
            AutomationRetryService retryService) {
        this.service = service; this.approvals = approvals; this.executions = executions;
        this.rules = rules; this.versions = versions; this.steps = steps; this.retryService = retryService;
    }

    @GetMapping
    public String index(Authentication authentication, @RequestParam(defaultValue = "PERSONAL") String scopeType,
            @RequestParam(required = false) UUID scopeId, Model model) {
        UUID actor = actor(authentication);
        AutomationScopeType scope = enumValue(scopeType, AutomationScopeType.class, AutomationScopeType.PERSONAL);
        UUID selectedScope = scope == AutomationScopeType.PERSONAL ? actor : scopeId;
        if (selectedScope == null) throw new AutomationRuleException("Choose a workspace to view its automations.");
        var ruleItems = service.list(actor, scope, selectedScope);
        var historyItems = service.history(actor, scope, selectedScope).stream().map(run -> {
            var rule = rules.findById(run.getRuleId()).orElseThrow();
            var version = versions.findByIdAndRuleId(run.getRuleVersionId(), rule.getId()).orElseThrow();
            return new HistoryItem(run, rule.getName(), version.getVersionNumber());
        }).toList();
        model.addAttribute("automationRules", ruleItems);
        model.addAttribute("enabledAutomationCount", ruleItems.stream()
                .filter(item -> item.rule().getStatus() == AutomationRuleStatus.ENABLED).count());
        model.addAttribute("automationHistory", historyItems);
        model.addAttribute("canManageAutomations", service.canManage(actor, scope, selectedScope));
        model.addAttribute("scopeType", scope.name()); model.addAttribute("scopeId", selectedScope);
        model.addAttribute("scopeName", scope == AutomationScopeType.PERSONAL ? "Personal space" : "Team workspace");
        model.addAttribute("activeNav", "automations");
        return "automation/index";
    }

    @GetMapping("/new")
    public String newRule(@RequestParam(defaultValue = "PERSONAL") String scopeType,
            @RequestParam(required = false) UUID scopeId, Authentication authentication, Model model) {
        AutomationRuleForm form = new AutomationRuleForm();
        var scope = enumValue(scopeType, AutomationScopeType.class, AutomationScopeType.PERSONAL);
        var selectedScope = scope == AutomationScopeType.PERSONAL ? actor(authentication) : scopeId;
        if (selectedScope == null) throw new AutomationRuleException("Choose a workspace first.");
        service.assertCanManage(actor(authentication), scope, selectedScope);
        form.setScopeType(scope.name());
        if (scopeId != null) form.setScopeId(scopeId.toString());
        model.addAttribute("ruleForm", form); prepareForm(model, null); return "automation/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, @RequestParam(defaultValue = "PERSONAL") String scopeType,
            @RequestParam(required = false) UUID scopeId, Authentication authentication, Model model) {
        AutomationScopeType scope = enumValue(scopeType, AutomationScopeType.class, AutomationScopeType.PERSONAL);
        UUID selected = scope == AutomationScopeType.PERSONAL ? actor(authentication) : scopeId;
        if (selected == null) throw new AutomationRuleException("Choose a workspace first.");
        var definition = service.definition(actor(authentication), id, scope, selected);
        var rule = service.find(id, scope, selected);
        service.assertCanManage(actor(authentication), scope, selected);
        var form = toForm(rule.getName(), rule.getDescription(), scope, selected, definition);
        model.addAttribute("ruleForm", form); prepareForm(model, id); return "automation/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("ruleForm") AutomationRuleForm form,
            BindingResult binding, Authentication authentication, Model model, RedirectAttributes redirect) {
        return save(null, form, binding, authentication, model, redirect);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("ruleForm") AutomationRuleForm form,
            BindingResult binding, Authentication authentication, Model model, RedirectAttributes redirect) {
        return save(id, form, binding, authentication, model, redirect);
    }

    @PostMapping("/{id}/enable")
    public String enable(@PathVariable UUID id, @RequestParam(defaultValue = "PERSONAL") String scopeType,
            @RequestParam(required = false) UUID scopeId, Authentication authentication, RedirectAttributes redirect) {
        var scope = enumValue(scopeType, AutomationScopeType.class, AutomationScopeType.PERSONAL);
        var selected = scope == AutomationScopeType.PERSONAL ? actor(authentication) : scopeId;
        service.enable(actor(authentication), id, scope, selected);
        redirect.addFlashAttribute("automationMessage", "Automation enabled."); return redirectTo(scope, selected);
    }

    @PostMapping("/{id}/disable")
    public String disable(@PathVariable UUID id, @RequestParam(defaultValue = "PERSONAL") String scopeType,
            @RequestParam(required = false) UUID scopeId, Authentication authentication, RedirectAttributes redirect) {
        var scope = enumValue(scopeType, AutomationScopeType.class, AutomationScopeType.PERSONAL);
        var selected = scope == AutomationScopeType.PERSONAL ? actor(authentication) : scopeId;
        service.disable(actor(authentication), id, scope, selected);
        redirect.addFlashAttribute("automationMessage", "Automation paused."); return redirectTo(scope, selected);
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable UUID id, @RequestParam(defaultValue = "PERSONAL") String scopeType,
            @RequestParam(required = false) UUID scopeId, Authentication authentication, RedirectAttributes redirect) {
        var scope = enumValue(scopeType, AutomationScopeType.class, AutomationScopeType.PERSONAL);
        var selected = scope == AutomationScopeType.PERSONAL ? actor(authentication) : scopeId;
        service.archive(actor(authentication), id, scope, selected);
        redirect.addFlashAttribute("automationMessage", "Automation archived."); return redirectTo(scope, selected);
    }

    @PostMapping("/{id}/dry-run")
    public String dryRun(@PathVariable UUID id, @RequestParam UUID taskId,
            @RequestParam(defaultValue = "PERSONAL") String scopeType, @RequestParam(required = false) UUID scopeId,
            Authentication authentication, RedirectAttributes redirect) {
        var scope = enumValue(scopeType, AutomationScopeType.class, AutomationScopeType.PERSONAL);
        var selected = scope == AutomationScopeType.PERSONAL ? actor(authentication) : scopeId;
        boolean matches = service.dryRun(actor(authentication), id, scope, selected, taskId);
        redirect.addFlashAttribute("automationMessage", matches
                ? "Dry run matched the task conditions. No actions were executed."
                : "Dry run did not match the task conditions. No actions were executed.");
        return redirectTo(scope, selected);
    }

    @GetMapping("/approvals")
    public String approvalInbox(@RequestParam UUID workspaceId, Authentication authentication, Model model) {
        UUID actor = actor(authentication);
        var inbox = service.pendingApprovals(actor, workspaceId).stream().map(request -> {
            var execution = executions.findById(request.getExecutionId()).orElseThrow();
            var rule = rules.findById(execution.getRuleId()).orElseThrow();
            var version = versions.findByIdAndRuleId(execution.getRuleVersionId(), rule.getId()).orElseThrow();
            var definition = service.read(version.getDefinitionJson());
            List<String> actionSummaries = definition.actions().stream().map(AutomationController::actionSummary).toList();
            return new ApprovalView(request, rule.getName(), rule.getDescription(), actionSummaries);
        }).toList();
        model.addAttribute("approvals", inbox);
        model.addAttribute("scopeId", workspaceId); model.addAttribute("activeNav", "automations");
        return "automation/approvals";
    }

    @PostMapping("/approvals/{id}/decision")
    public String decide(@PathVariable UUID id, @RequestParam UUID workspaceId, @RequestParam boolean approve,
            @RequestParam(required = false) String reason, Authentication authentication, RedirectAttributes redirect) {
        approvals.decide(actor(authentication), workspaceId, id, approve, reason);
        redirect.addFlashAttribute("automationMessage", approve ? "Workflow approved." : "Workflow rejected.");
        return "redirect:/automations/approvals?workspaceId=" + workspaceId;
    }

    @GetMapping("/executions/{id}")
    public String execution(@PathVariable UUID id, @RequestParam(defaultValue = "PERSONAL") String scopeType,
            @RequestParam(required = false) UUID scopeId, Authentication authentication, Model model) {
        var scope = enumValue(scopeType, AutomationScopeType.class, AutomationScopeType.PERSONAL);
        var selected = scope == AutomationScopeType.PERSONAL ? actor(authentication) : scopeId;
        var execution = executions.findByIdAndScopeTypeAndScopeId(id, scope, selected)
                .orElseThrow(() -> new AutomationRuleException("That execution is not available."));
        if (scope == AutomationScopeType.PERSONAL && !selected.equals(actor(authentication)))
            throw new AutomationRuleException("That execution is not available.");
        if (scope == AutomationScopeType.WORKSPACE) service.list(actor(authentication), scope, selected);
        model.addAttribute("execution", execution); model.addAttribute("steps", steps.findAllByExecutionIdOrderByStepIndexAsc(id));
        model.addAttribute("canManageAutomations", service.canManage(actor(authentication), scope, selected));
        model.addAttribute("scopeType", scope.name()); model.addAttribute("scopeId", selected); model.addAttribute("activeNav", "automations");
        return "automation/execution";
    }

    @PostMapping("/executions/{id}/retry")
    public String retry(@PathVariable UUID id, @RequestParam(defaultValue = "PERSONAL") String scopeType,
            @RequestParam(required = false) UUID scopeId, Authentication authentication, RedirectAttributes redirect) {
        var scope = enumValue(scopeType, AutomationScopeType.class, AutomationScopeType.PERSONAL);
        var selected = scope == AutomationScopeType.PERSONAL ? actor(authentication) : scopeId;
        retryService.retry(actor(authentication), id, scope, selected);
        redirect.addFlashAttribute("automationMessage", "Retry queued. Successful steps will be skipped.");
        return "redirect:/automations/executions/" + id + "?scopeType=" + scope + (scope == AutomationScopeType.PERSONAL ? "" : "&scopeId=" + selected);
    }

    private String save(UUID id, AutomationRuleForm form, BindingResult binding, Authentication authentication,
            Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) { prepareForm(model, id); model.addAttribute("formError", "Review the highlighted fields."); return "automation/form"; }
        try {
            AutomationScopeType scope = enumValue(form.getScopeType(), AutomationScopeType.class, null);
            UUID scopeId = scope == AutomationScopeType.PERSONAL ? actor(authentication) : parseUuid(form.getScopeId(), "Choose a team workspace.");
            UUID saved = service.save(actor(authentication), id, scope, scopeId, form.getName(), form.getDescription(), definition(form));
            redirect.addFlashAttribute("automationMessage", "Rule version saved as a draft.");
            return redirectTo(scope, scopeId);
        } catch (RuntimeException ex) {
            prepareForm(model, id); model.addAttribute("formError", ex instanceof AutomationRuleException
                    ? ex.getMessage() : "Check the workflow fields and try again."); return "automation/form";
        }
    }

    private void prepareForm(Model model, UUID editingId) {
        model.addAttribute("editingId", editingId); model.addAttribute("triggers", AutomationTriggerType.values());
        model.addAttribute("fields", AutomationConditionField.values()); model.addAttribute("operators", AutomationComparisonOperator.values());
        model.addAttribute("actions", AutomationActionType.values()); model.addAttribute("statuses", com.taskos.task.entity.TaskStatus.values());
        model.addAttribute("priorities", com.taskos.task.entity.TaskPriority.values()); model.addAttribute("days", DayOfWeek.values());
        model.addAttribute("frequencies", AutomationScheduleFrequency.values()); model.addAttribute("misfirePolicies", AutomationMisfirePolicy.values());
        model.addAttribute("activeNav", "automations");
    }

    private AutomationRuleDefinition definition(AutomationRuleForm form) {
        var trigger = enumValue(form.getTriggerType(), AutomationTriggerType.class, null);
        var logic = enumValue(form.getConditionLogic(), AutomationConditionLogic.class, null);
        List<AutomationCondition> conditions = new ArrayList<>();
        for (int i = 0; i < form.getConditionFields().size(); i++) {
            String field = form.getConditionFields().get(i);
            if (field == null || field.isBlank()) continue;
            var conditionField = enumValue(field, AutomationConditionField.class, null);
            var operator = enumValue(form.getConditionOperators().get(i), AutomationComparisonOperator.class, null);
            String raw = form.getConditionValues().get(i);
            List<String> values = operator == AutomationComparisonOperator.IS_EMPTY || operator == AutomationComparisonOperator.IS_NOT_EMPTY
                    ? List.of() : operator == AutomationComparisonOperator.IN_LIST
                    ? java.util.Arrays.stream((raw == null ? "" : raw).split(",")).map(String::strip).filter(value -> !value.isEmpty()).toList()
                    : List.of(raw == null ? "" : raw.strip());
            conditions.add(new AutomationCondition(conditionField, operator, values));
        }
        AutomationConditionGroup group = conditions.isEmpty() ? null : new AutomationConditionGroup(logic, conditions, List.of());
        List<AutomationActionDefinition> actionList = new ArrayList<>();
        for (int i = 0; i < form.getActionTypes().size(); i++) {
            String selected = form.getActionTypes().get(i);
            if (selected == null || selected.isBlank()) continue;
            AutomationActionType type = enumValue(selected, AutomationActionType.class, null);
            Map<String, String> parameters = new LinkedHashMap<>();
            switch (type) {
                case CREATE_TASK -> {
                    parameters.put("title", form.getActionTitles().get(i));
                    parameters.put("description", form.getActionDescriptions().get(i));
                    parameters.put("priority", form.getActionPriorities().get(i));
                    if (!form.getActionDueDates().get(i).isBlank()) parameters.put("dueDate", form.getActionDueDates().get(i));
                }
                case UPDATE_TASK_STATUS -> parameters.put("status", form.getActionStatuses().get(i));
                case SET_TASK_PRIORITY -> parameters.put("priority", form.getActionPriorities().get(i));
                case CREATE_REMINDER -> {
                    parameters.put("title", form.getActionTitles().get(i));
                    parameters.put("offsetMinutes", form.getReminderOffsets().get(i));
                }
                case ASSIGN_TASK_TO_TRIGGER_ACTOR -> { }
            }
            actionList.add(new AutomationActionDefinition(type, parameters));
        }
        AutomationScheduleDefinition schedule = null;
        if (trigger == AutomationTriggerType.SCHEDULED) {
            schedule = new AutomationScheduleDefinition(
                    enumValue(form.getScheduleFrequency(), AutomationScheduleFrequency.class, null),
                    "WEEKLY".equals(form.getScheduleFrequency()) ? enumValue(form.getScheduleDay(), DayOfWeek.class, null) : null,
                    LocalTime.parse(form.getScheduleTime()),
                    form.getScheduleTimezone(), enumValue(form.getMisfirePolicy(), AutomationMisfirePolicy.class, null));
        }
        return new AutomationRuleDefinition(1, trigger, group, actionList, form.isRequiresApproval(), schedule, form.getMaxAttempts());
    }

    private AutomationRuleForm toForm(String name, String description, AutomationScopeType scope, UUID scopeId,
            AutomationRuleDefinition definition) {
        AutomationRuleForm form = new AutomationRuleForm(); form.setName(name); form.setDescription(description);
        form.setScopeType(scope.name()); form.setScopeId(scopeId.toString()); form.setTriggerType(definition.triggerType().name());
        form.setRequiresApproval(definition.requiresApproval()); form.setMaxAttempts(definition.maxAttempts());
        List<String> fields = new ArrayList<>(), operators = new ArrayList<>(), values = new ArrayList<>();
        if (definition.conditions() != null) {
            form.setConditionLogic(definition.conditions().logic().name());
            for (var condition : definition.conditions().conditions()) {
                fields.add(condition.field().name()); operators.add(condition.operator().name());
                values.add(String.join(", ", condition.values()));
            }
        }
        form.setConditionFields(fields); form.setConditionOperators(operators); form.setConditionValues(values);
        List<String> types = new ArrayList<>(), titles = new ArrayList<>(), descriptions = new ArrayList<>(), statuses = new ArrayList<>(), priorities = new ArrayList<>(), dueDates = new ArrayList<>(), offsets = new ArrayList<>();
        for (var action : definition.actions()) {
            types.add(action.type().name()); var p = action.parameters();
            titles.add(p.getOrDefault("title", "")); descriptions.add(p.getOrDefault("description", ""));
            statuses.add(p.getOrDefault("status", "IN_PROGRESS")); priorities.add(p.getOrDefault("priority", "MEDIUM"));
            dueDates.add(p.getOrDefault("dueDate", "")); offsets.add(p.getOrDefault("offsetMinutes", "60"));
        }
        form.setActionTypes(types); form.setActionTitles(titles); form.setActionDescriptions(descriptions);
        form.setActionStatuses(statuses); form.setActionPriorities(priorities); form.setActionDueDates(dueDates); form.setReminderOffsets(offsets);
        if (definition.schedule() != null) {
            form.setScheduleFrequency(definition.schedule().frequency().name()); form.setScheduleDay(definition.schedule().dayOfWeek() == null ? "MONDAY" : definition.schedule().dayOfWeek().name());
            form.setScheduleTime(definition.schedule().localTime().toString()); form.setScheduleTimezone(definition.schedule().timezone());
            form.setMisfirePolicy(definition.schedule().misfirePolicy().name());
        }
        return form;
    }

    private static UUID actor(Authentication authentication) { return ((TaskOsUserDetails) authentication.getPrincipal()).getId(); }
    private static String actionSummary(AutomationActionDefinition action) {
        Map<String, String> p = action.parameters();
        return switch (action.type()) {
            case CREATE_TASK -> "Create task “" + p.getOrDefault("title", "Untitled") + "” · " + p.getOrDefault("priority", "MEDIUM") + " priority";
            case UPDATE_TASK_STATUS -> "Set source task status to " + p.get("status");
            case SET_TASK_PRIORITY -> "Set source task priority to " + p.get("priority");
            case CREATE_REMINDER -> "Schedule in-app reminder “" + p.getOrDefault("title", "Reminder") + "”";
            case ASSIGN_TASK_TO_TRIGGER_ACTOR -> "Assign task to the member who triggered the event";
        };
    }
    private static UUID parseUuid(String value, String message) { try { return UUID.fromString(value); } catch (RuntimeException ex) { throw new AutomationRuleException(message); } }
    private static <E extends Enum<E>> E enumValue(String value, Class<E> type, E fallback) {
        try { return Enum.valueOf(type, value); } catch (RuntimeException ex) {
            if (fallback != null) return fallback;
            throw new AutomationRuleException("Choose a valid " + type.getSimpleName().replace("Automation", "").toLowerCase() + ".");
        }
    }
    private String redirectTo(AutomationScopeType scope, UUID scopeId) {
        return "redirect:/automations?scopeType=" + scope.name() + (scope == AutomationScopeType.PERSONAL ? "" : "&scopeId=" + scopeId);
    }
    public record ApprovalView(AutomationApprovalRequest request, String ruleName, String ruleDescription, List<String> actions) { }
    public record HistoryItem(AutomationExecution execution, String ruleName, int versionNumber) { }
}
