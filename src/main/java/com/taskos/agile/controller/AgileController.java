package com.taskos.agile.controller;

import com.taskos.agile.entity.AgileProjectSettings;
import com.taskos.agile.entity.AgileWorkItem;
import com.taskos.agile.entity.AgileWorkItemType;
import com.taskos.agile.service.AgileService;
import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.collaboration.service.ProjectAccessService;
import com.taskos.collaboration.service.WorkspacePermission;
import com.taskos.project.entity.Project;
import com.taskos.project.service.ProjectService;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.service.TaskRuleException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@Transactional
public class AgileController {
    private final AgileService agile;
    private final ProjectService projects;
    private final ProjectAccessService projectAccess;

    public AgileController(AgileService agile, ProjectService projects, ProjectAccessService projectAccess) {
        this.agile = agile; this.projects = projects; this.projectAccess = projectAccess;
    }

    @GetMapping("/projects/{projectId}/agile")
    public String agileHome(@PathVariable UUID projectId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        AgileProjectSettings settings = agile.projectSettings(ownerId, projectId);
        page(model, userId(auth), ownerId, project, "agile", "Agile workspace"); model.addAttribute("settings", settings);
        if (settings != null && settings.isEnabled()) {
            model.addAttribute("activeSprint", agile.activeSprint(ownerId, projectId));
            model.addAttribute("sprints", agile.sprints(ownerId, projectId));
            model.addAttribute("backlogCount", agile.backlogItems(ownerId, projectId).size());
            model.addAttribute("epicProgress", agile.epicProgress(ownerId, projectId));
            model.addAttribute("activity", agile.recentActivity(ownerId, projectId));
            model.addAttribute("meanVelocity", agile.averageVelocity(ownerId, projectId));
        }
        return "agile/workspace";
    }

    @PostMapping("/projects/{projectId}/agile")
    public String configure(@PathVariable UUID projectId, @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam(required = false) String definitionOfDone, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.configure(projectOwner(auth, projectId, true), projectId, enabled, definitionOfDone),
                "/projects/" + projectId + "/agile", enabled ? "Scrum enabled for this project." : "Scrum disabled; its history is preserved.");
    }

    @GetMapping("/projects/{projectId}/backlog")
    public String backlog(@PathVariable UUID projectId, @RequestParam(required = false) String q,
            @RequestParam(required = false) AgileWorkItemType type, @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID epicId, @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String sprint, @RequestParam(defaultValue = "false") boolean unestimated,
            Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        page(model, userId(auth), ownerId, project, "backlog", "Product backlog");
        model.addAttribute("items", agile.backlog(ownerId, projectId, q, type, priority, epicId, status, sprint, unestimated));
        model.addAttribute("epics", agile.epics(ownerId, projectId));
        model.addAttribute("workItemTypes", AgileWorkItemType.values()); model.addAttribute("priorities", TaskPriority.values());
        model.addAttribute("storyPointScale", AgileWorkItem.STORY_POINT_SCALE);
        model.addAttribute("taskStatuses", TaskStatus.values()); model.addAttribute("q", q); model.addAttribute("filterType", type);
        model.addAttribute("filterPriority", priority); model.addAttribute("filterEpic", epicId);
        model.addAttribute("filterStatus", status); model.addAttribute("filterSprint", sprint);
        model.addAttribute("unestimated", unestimated); model.addAttribute("settings", agile.projectSettings(ownerId, projectId));
        return "agile/workspace";
    }

    @PostMapping("/projects/{projectId}/backlog/create")
    public String createWorkItem(@PathVariable UUID projectId, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) LocalDate dueDate, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.createWorkItem(userId(auth), projectOwner(auth, projectId, true), projectId, title, description, priority, dueDate),
                "/projects/" + projectId + "/backlog", "Work item added to the product backlog.");
    }

    @PostMapping("/projects/{projectId}/backlog/{taskId}")
    public String updateWorkItem(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @RequestParam AgileWorkItemType type, @RequestParam(required = false) Integer storyPoints,
            @RequestParam(required = false) UUID epicId, @RequestParam(required = false) String definitionOfDone,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.updateWorkItem(projectOwner(auth, projectId, true), projectId, taskId, type, storyPoints, epicId, definitionOfDone),
                "/projects/" + projectId + "/backlog", "Work item updated.");
    }

    @PostMapping("/projects/{projectId}/backlog/{taskId}/order")
    public String reorderBacklog(@PathVariable UUID projectId, @PathVariable UUID taskId, @RequestParam int direction,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.reorderBacklog(projectOwner(auth, projectId, true), projectId, taskId, direction),
                "/projects/" + projectId + "/backlog", "Product backlog order saved.");
    }

    @PostMapping("/projects/{projectId}/backlog/order")
    public String dragReorderBacklog(@PathVariable UUID projectId, @RequestParam java.util.List<UUID> taskIds,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.reorderBacklog(projectOwner(auth, projectId, true), projectId, taskIds),
                "/projects/" + projectId + "/backlog", "Product backlog order saved.");
    }

    @PostMapping("/projects/{projectId}/backlog/{taskId}/criteria")
    public String addCriterion(@PathVariable UUID projectId, @PathVariable UUID taskId, @RequestParam String criterion,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.addCriterion(projectOwner(auth, projectId, true), projectId, taskId, criterion),
                "/projects/" + projectId + "/backlog", "Acceptance criterion added.");
    }

    @PostMapping("/projects/{projectId}/backlog/{taskId}/criteria/{criterionId}")
    public String updateCriterion(@PathVariable UUID projectId, @PathVariable UUID taskId, @PathVariable UUID criterionId,
            @RequestParam String criterion, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.updateCriterion(projectOwner(auth, projectId, true), projectId, taskId, criterionId, criterion),
                "/projects/" + projectId + "/backlog", "Acceptance criterion saved.");
    }

    @PostMapping("/projects/{projectId}/backlog/{taskId}/criteria/{criterionId}/satisfied")
    public String satisfyCriterion(@PathVariable UUID projectId, @PathVariable UUID taskId, @PathVariable UUID criterionId,
            @RequestParam(defaultValue = "false") boolean satisfied, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.satisfyCriterion(projectOwner(auth, projectId, true), projectId, taskId, criterionId, satisfied),
                "/projects/" + projectId + "/backlog", satisfied ? "Criterion marked satisfied." : "Criterion reopened.");
    }

    @PostMapping("/projects/{projectId}/backlog/{taskId}/criteria/{criterionId}/delete")
    public String removeCriterion(@PathVariable UUID projectId, @PathVariable UUID taskId, @PathVariable UUID criterionId,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.removeCriterion(projectOwner(auth, projectId, true), projectId, taskId, criterionId),
                "/projects/" + projectId + "/backlog", "Acceptance criterion removed.");
    }

    @PostMapping("/projects/{projectId}/backlog/{taskId}/dod")
    public String addDefinitionOfDoneCriterion(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @RequestParam String criterion, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.addDefinitionOfDoneCriterion(projectOwner(auth, projectId, true), projectId, taskId, criterion),
                "/projects/" + projectId + "/backlog", "Definition of Done item added.");
    }

    @PostMapping("/projects/{projectId}/backlog/{taskId}/dod/{criterionId}/satisfied")
    public String satisfyDefinitionOfDoneCriterion(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @PathVariable UUID criterionId, @RequestParam(defaultValue = "false") boolean satisfied,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.satisfyDefinitionOfDoneCriterion(projectOwner(auth, projectId, true), projectId, taskId, criterionId, satisfied),
                "/projects/" + projectId + "/backlog", satisfied ? "Definition of Done item checked." : "Definition of Done item reopened.");
    }

    @PostMapping("/projects/{projectId}/backlog/{taskId}/criteria/{criterionId}/order")
    public String reorderCriterion(@PathVariable UUID projectId, @PathVariable UUID taskId, @PathVariable UUID criterionId,
            @RequestParam int direction, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.reorderCriterion(projectOwner(auth, projectId, true), projectId, taskId, criterionId, direction),
                "/projects/" + projectId + "/backlog", "Acceptance criterion order saved.");
    }

    @GetMapping("/projects/{projectId}/epics")
    public String epics(@PathVariable UUID projectId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        page(model, userId(auth), ownerId, project, "epics", "Epics"); model.addAttribute("epics", agile.epics(ownerId, projectId));
        model.addAttribute("epicProgress", agile.epicProgress(ownerId, projectId)); return "agile/workspace";
    }

    @PostMapping("/projects/{projectId}/epics")
    public String createEpic(@PathVariable UUID projectId, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) LocalDate targetDate,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.createEpic(projectOwner(auth, projectId, true), projectId, title, description, targetDate),
                "/projects/" + projectId + "/epics", "Epic created.");
    }

    @PostMapping("/projects/{projectId}/epics/{epicId}")
    public String updateEpic(@PathVariable UUID projectId, @PathVariable UUID epicId, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) LocalDate targetDate,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.updateEpic(projectOwner(auth, projectId, true), projectId, epicId, title, description, targetDate),
                "/projects/" + projectId + "/epics", "Epic updated.");
    }

    @PostMapping("/projects/{projectId}/epics/{epicId}/archive")
    public String archiveEpic(@PathVariable UUID projectId, @PathVariable UUID epicId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.archiveEpic(projectOwner(auth, projectId, true), projectId, epicId),
                "/projects/" + projectId + "/epics", "Epic archived. Its work items remain in the backlog.");
    }

    @GetMapping("/projects/{projectId}/sprints")
    public String sprints(@PathVariable UUID projectId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        page(model, userId(auth), ownerId, project, "sprints", "Sprints"); model.addAttribute("sprints", agile.sprints(ownerId, projectId));
        model.addAttribute("activeSprint", agile.activeSprint(ownerId, projectId));
        model.addAttribute("meanVelocity", agile.averageVelocity(ownerId, projectId)); return "agile/workspace";
    }

    @GetMapping("/projects/{projectId}/sprints/new")
    public String newSprint(@PathVariable UUID projectId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW); page(model, userId(auth), ownerId, project, "sprint-form", "Plan a sprint");
        model.addAttribute("sprints", agile.sprints(ownerId, projectId)); model.addAttribute("suggestedStart", LocalDate.now(ZoneOffset.UTC));
        model.addAttribute("suggestedEnd", LocalDate.now(ZoneOffset.UTC).plusDays(13)); return "agile/workspace";
    }

    @PostMapping("/projects/{projectId}/sprints")
    public String createSprint(@PathVariable UUID projectId, @RequestParam String name, @RequestParam String goal,
            @RequestParam(required = false) String planningNotes, @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, @RequestParam(required = false) Integer capacityPoints,
            Authentication auth, RedirectAttributes redirect) {
        try {
            var sprint = agile.createSprint(projectOwner(auth, projectId, true), projectId, name, goal, planningNotes, startDate, endDate, capacityPoints);
            redirect.addFlashAttribute("taskMessage", "Sprint planned.");
            return "redirect:/projects/" + projectId + "/sprints/" + sprint.getId() + "/planning";
        } catch (TaskRuleException ex) { redirect.addFlashAttribute("taskError", ex.getMessage()); return "redirect:/projects/" + projectId + "/sprints/new"; }
    }

    @GetMapping("/projects/{projectId}/sprints/{sprintId}/planning")
    public String planning(@PathVariable UUID projectId, @PathVariable UUID sprintId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        var sprint = agile.sprint(ownerId, projectId, sprintId); page(model, userId(auth), ownerId, project, "planning", "Sprint planning");
        model.addAttribute("sprint", sprint); model.addAttribute("items", agile.backlogItems(ownerId, projectId));
        model.addAttribute("sprintItems", agile.planningItems(ownerId, projectId, sprintId));
        model.addAttribute("metrics", agile.metrics(ownerId, projectId, sprintId));
        model.addAttribute("overlapWarning", agile.hasPlannedOverlap(ownerId, projectId, sprintId));
        model.addAttribute("meanVelocity", agile.averageVelocity(ownerId, projectId));
        model.addAttribute("sprints", agile.sprints(ownerId, projectId));
        return "agile/workspace";
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/items/{taskId}/add")
    public String addToSprint(@PathVariable UUID projectId, @PathVariable UUID sprintId, @PathVariable UUID taskId,
            @RequestParam(required = false) String reason, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.addToSprint(projectOwner(auth, projectId, true), projectId, sprintId, taskId, reason),
                "/projects/" + projectId + "/sprints/" + sprintId + "/planning", "Work added to the sprint.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/items/{taskId}/remove")
    public String removeFromSprint(@PathVariable UUID projectId, @PathVariable UUID sprintId, @PathVariable UUID taskId,
            @RequestParam(required = false) String reason, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.removeFromSprint(projectOwner(auth, projectId, true), projectId, sprintId, taskId, reason),
                "/projects/" + projectId + "/sprints/" + sprintId + "/planning", "Work removed from the sprint scope.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/items/{taskId}/order")
    public String reorderSprintItem(@PathVariable UUID projectId, @PathVariable UUID sprintId, @PathVariable UUID taskId,
            @RequestParam int direction, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.reorderSprintItem(projectOwner(auth, projectId, true), projectId, sprintId, taskId, direction),
                "/projects/" + projectId + "/sprints/" + sprintId + "/planning", "Sprint backlog order saved.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/start")
    public String startSprint(@PathVariable UUID projectId, @PathVariable UUID sprintId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.startSprint(projectOwner(auth, projectId, true), projectId, sprintId),
                "/projects/" + projectId + "/sprints/active", "Sprint started.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/update")
    public String updateSprint(@PathVariable UUID projectId, @PathVariable UUID sprintId, @RequestParam String name,
            @RequestParam String goal, @RequestParam(required = false) String planningNotes,
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
            @RequestParam(required = false) Integer capacityPoints, @RequestParam(required = false) String reason,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.updateSprint(projectOwner(auth, projectId, true), projectId, sprintId, name, goal, planningNotes,
                startDate, endDate, capacityPoints, reason), "/projects/" + projectId + "/sprints/" + sprintId + "/planning", "Sprint planning saved.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/complete")
    public String completeSprint(@PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestParam String incompletePolicy, @RequestParam(required = false) UUID targetSprintId,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.completeSprint(projectOwner(auth, projectId, true), projectId, sprintId, incompletePolicy, targetSprintId),
                "/projects/" + projectId + "/sprints/history", "Sprint completed. Delivered points are now part of project velocity.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/cancel")
    public String cancelSprint(@PathVariable UUID projectId, @PathVariable UUID sprintId, @RequestParam String reason,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.cancelSprint(projectOwner(auth, projectId, true), projectId, sprintId, reason),
                "/projects/" + projectId + "/sprints/history", "Sprint cancelled; its work remains in the product backlog.");
    }

    @GetMapping("/projects/{projectId}/sprints/active")
    public String activeSprint(@PathVariable UUID projectId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        page(model, userId(auth), ownerId, project, "active", "Active sprint"); var sprint = agile.activeSprint(ownerId, projectId);
        model.addAttribute("sprint", sprint);
        if (sprint != null) {
            sprintPageModel(model, ownerId, projectId, sprint.getId());
            model.addAttribute("board", agile.activeBoard(ownerId, projectId, sprint.getId()));
            model.addAttribute("columns", project.getWorkspaceId() == null
                    ? projects.columns(ownerId, projectId) : projects.sharedColumns(userId(auth), projectId));
            model.addAttribute("sprints", agile.sprints(ownerId, projectId));
            model.addAttribute("impediments", agile.impediments(ownerId, projectId, sprint.getId()));
            var burndown = agile.burndown(ownerId, projectId, sprint.getId());
            int chartMax = Math.max(1, burndown.stream()
                    .mapToInt(point -> Math.max(point.idealRemainingPoints(), point.actualRemainingPoints() == null ? 0 : point.actualRemainingPoints()))
                    .max().orElse(1));
            model.addAttribute("chartMax", chartMax); model.addAttribute("idealPath", chartPath(burndown, false, chartMax));
            model.addAttribute("actualPath", chartPath(burndown, true, chartMax));
        }
        return "agile/workspace";
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/board/{taskId}/move")
    public String moveSprintTask(@PathVariable UUID projectId, @PathVariable UUID sprintId, @PathVariable UUID taskId,
            @RequestParam UUID columnId, @RequestParam(defaultValue = "0") int position,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.moveActiveTask(userId(auth), projectOwner(auth, projectId, true), projectId, sprintId, taskId, columnId, position),
                "/projects/" + projectId + "/sprints/active", "Sprint board updated.");
    }

    @GetMapping("/projects/{projectId}/sprints/{sprintId}")
    public String sprintSummary(@PathVariable UUID projectId, @PathVariable UUID sprintId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); var sprint = agile.sprint(ownerId, projectId, sprintId);
        if (sprint.getStatus().name().equals("PLANNED")) return "redirect:/projects/" + projectId + "/sprints/" + sprintId + "/planning";
        if (sprint.getStatus().name().equals("ACTIVE")) return "redirect:/projects/" + projectId + "/sprints/active";
        Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW); page(model, userId(auth), ownerId, project, "summary", "Sprint summary");
        model.addAttribute("sprint", sprint); model.addAttribute("metrics", agile.metrics(ownerId, projectId, sprintId));
        model.addAttribute("baselineItems", agile.baselineItems(ownerId, projectId, sprintId));
        model.addAttribute("membershipHistory", agile.membershipHistory(ownerId, projectId, sprintId));
        model.addAttribute("activity", agile.sprintActivity(ownerId, projectId, sprintId));
        model.addAttribute("burndown", agile.burndown(ownerId, projectId, sprintId));
        model.addAttribute("ceremony", agile.ceremony(ownerId, projectId, sprintId)); return "agile/workspace";
    }

    @GetMapping("/projects/{projectId}/sprints/{sprintId}/review")
    public String review(@PathVariable UUID projectId, @PathVariable UUID sprintId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        page(model, userId(auth), ownerId, project, "review", "Sprint review"); sprintPageModel(model, ownerId, projectId, sprintId); return "agile/workspace";
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/review")
    public String saveReview(@PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestParam(required = false) String outcome, @RequestParam(required = false) String delivered,
            @RequestParam(required = false) String incomplete, @RequestParam(required = false) String demo,
            @RequestParam(required = false) String feedback, @RequestParam(required = false) String accepted,
            @RequestParam(required = false) String needsRework, @RequestParam(required = false) String followUps,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.saveReview(projectOwner(auth, projectId, true), projectId, sprintId, outcome, delivered, incomplete, demo, feedback, accepted, needsRework, followUps),
                "/projects/" + projectId + "/sprints/" + sprintId + "/review", "Sprint review notes saved.");
    }

    @GetMapping("/projects/{projectId}/sprints/{sprintId}/retrospective")
    public String retrospective(@PathVariable UUID projectId, @PathVariable UUID sprintId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        page(model, userId(auth), ownerId, project, "retrospective", "Sprint retrospective"); sprintPageModel(model, ownerId, projectId, sprintId);
        model.addAttribute("actionItems", agile.retrospectiveActions(ownerId, projectId, sprintId)); return "agile/workspace";
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/retrospective")
    public String saveRetrospective(@PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestParam(required = false) String wentWell, @RequestParam(required = false) String improve,
            @RequestParam(required = false) String tryNext, @RequestParam(required = false) String actions,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.saveRetrospective(projectOwner(auth, projectId, true), projectId, sprintId, wentWell, improve, tryNext, actions),
                "/projects/" + projectId + "/sprints/" + sprintId + "/retrospective", "Retrospective notes saved.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/actions")
    public String addRetrospectiveAction(@PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestParam String description, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.addRetrospectiveAction(projectOwner(auth, projectId, true), projectId, sprintId, description),
                "/projects/" + projectId + "/sprints/" + sprintId + "/retrospective", "Retrospective action item saved.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/actions/{actionId}/convert")
    public String convertRetrospectiveAction(@PathVariable UUID projectId, @PathVariable UUID sprintId,
            @PathVariable UUID actionId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.convertRetrospectiveAction(userId(auth), projectOwner(auth, projectId, true), projectId, sprintId, actionId),
                "/projects/" + projectId + "/sprints/" + sprintId + "/retrospective", "Action item linked to a project task.");
    }

    @GetMapping("/projects/{projectId}/sprints/{sprintId}/daily")
    public String daily(@PathVariable UUID projectId, @PathVariable UUID sprintId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        page(model, userId(auth), ownerId, project, "daily", "Daily Scrum and impediments"); sprintPageModel(model, ownerId, projectId, sprintId);
        model.addAttribute("dailyNotes", agile.dailyNotes(ownerId, projectId, sprintId));
        model.addAttribute("impediments", agile.impediments(ownerId, projectId, sprintId));
        model.addAttribute("items", agile.planningItems(ownerId, projectId, sprintId)); return "agile/workspace";
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/daily")
    public String addDailyNote(@PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestParam(required = false) String progress, @RequestParam(required = false) String next,
            @RequestParam(required = false) String blockers, @RequestParam(required = false) String notes,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.addDailyNote(projectOwner(auth, projectId, true), projectId, sprintId, progress, next, blockers, notes),
                "/projects/" + projectId + "/sprints/" + sprintId + "/daily", "Daily Scrum note saved.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/impediments")
    public String addImpediment(@PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestParam(required = false) UUID taskId, @RequestParam String description,
            @RequestParam(required = false) String notes, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.addImpediment(projectOwner(auth, projectId, true), projectId, sprintId, taskId, description, notes),
                "/projects/" + projectId + "/sprints/" + sprintId + "/daily", "Impediment recorded.");
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/impediments/{impedimentId}/resolve")
    public String resolveImpediment(@PathVariable UUID projectId, @PathVariable UUID sprintId, @PathVariable UUID impedimentId,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> agile.resolveImpediment(projectOwner(auth, projectId, true), projectId, sprintId, impedimentId),
                "/projects/" + projectId + "/sprints/" + sprintId + "/daily", "Impediment resolved.");
    }

    @GetMapping({"/projects/{projectId}/sprints/history", "/projects/{projectId}/sprints/velocity"})
    public String history(@PathVariable UUID projectId, Authentication auth, Model model) {
        UUID ownerId = projectOwner(auth, projectId, false); Project project = projectAccess.require(userId(auth), projectId, WorkspacePermission.VIEW);
        page(model, userId(auth), ownerId, project, "history", "Sprint history and velocity");
        model.addAttribute("sprints", agile.sprints(ownerId, projectId)); model.addAttribute("completedSprints", agile.velocity(ownerId, projectId));
        model.addAttribute("meanVelocity", agile.averageVelocity(ownerId, projectId)); return "agile/workspace";
    }

    private void sprintPageModel(Model model, UUID ownerId, UUID projectId, UUID sprintId) {
        var sprint = agile.sprint(ownerId, projectId, sprintId);
        model.addAttribute("sprint", sprint); model.addAttribute("metrics", agile.metrics(ownerId, projectId, sprintId));
        model.addAttribute("ceremony", agile.ceremony(ownerId, projectId, sprintId));
        model.addAttribute("burndown", agile.burndown(ownerId, projectId, sprintId));
        model.addAttribute("activity", agile.sprintActivity(ownerId, projectId, sprintId));
    }

    private String chartPath(java.util.List<com.taskos.agile.dto.BurndownPoint> points, boolean actual, int max) {
        if (points.size() < 2) return "";
        java.util.List<String> coordinates = new java.util.ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            Integer value;
            if (actual) value = point.actualRemainingPoints();
            else value = point.idealRemainingPoints();
            if (value == null) continue;
            double x = 50.0 + (640.0 * i / (points.size() - 1));
            double y = 230.0 - (200.0 * value / max);
            coordinates.add(String.format(java.util.Locale.ROOT, "%.1f,%.1f", x, y));
        }
        return String.join(" ", coordinates);
    }

    private void page(Model model, UUID actorId, UUID ownerId, Project project, String view, String title) {
        model.addAttribute("project", project); model.addAttribute("view", view);
        model.addAttribute("canEditProject", projectAccess.canEdit(actorId, project.getId()));
        model.addAttribute("settings", agile.projectSettings(ownerId, project.getId()));
        model.addAttribute("pageTitle", title + " — " + project.getName() + " — TASK OS");
        model.addAttribute("description", "Scrum planning and execution for the canonical project tasks.");
        model.addAttribute("activeNav", "projects");
    }

    private String action(RedirectAttributes redirect, Runnable operation, String destination, String success) {
        try { operation.run(); redirect.addFlashAttribute("taskMessage", success); }
        catch (TaskRuleException ex) { redirect.addFlashAttribute("taskError", ex.getMessage()); }
        return "redirect:" + destination;
    }

    private UUID projectOwner(Authentication auth, UUID projectId, boolean write) {
        UUID actorId = ((TaskOsUserDetails) auth.getPrincipal()).getId();
        Project project = projectAccess.require(actorId, projectId,
                write ? WorkspacePermission.EDIT_TASK : WorkspacePermission.VIEW);
        return project.getOwner().getId();
    }

    private UUID userId(Authentication auth) { return ((TaskOsUserDetails) auth.getPrincipal()).getId(); }
}

