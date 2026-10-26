package com.taskos.project.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.project.dto.ProjectForm;
import com.taskos.project.entity.*;
import com.taskos.project.service.ProjectService;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.service.TaskRuleException;
import com.taskos.task.service.TaskTagService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProjectController {
    private final ProjectService projects;
    private final TaskTagService tags;

    public ProjectController(ProjectService projects, TaskTagService tags) { this.projects = projects; this.tags = tags; }

    @GetMapping("/projects")
    public String hub(@RequestParam(required = false) String q, @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) Boolean archived, @RequestParam(defaultValue = "0") int page,
            Authentication auth, Model model) {
        Page<Project> result = projects.search(owner(auth), q, status, archived, page);
        model.addAttribute("projectPage", result); model.addAttribute("query", q);
        model.addAttribute("selectedStatus", status); model.addAttribute("archived", archived);
        model.addAttribute("statuses", ProjectStatus.values()); model.addAttribute("activeNav", "projects");
        model.addAttribute("activeProjects", projects.activeProjectCount(owner(auth)));
        return page(model, "Projects", "Plan work with real tasks, milestones and Kanban workflows.", "projects/hub");
    }

    @GetMapping("/projects/new")
    public String createForm(@RequestParam(required = false) UUID templateId, Authentication auth, Model model) {
        ProjectForm form = new ProjectForm();
        model.addAttribute("projectForm", form); model.addAttribute("editing", false);
        model.addAttribute("selectedTemplate", templateId);
        model.addAttribute("templatePreview", templateId == null ? null : projects.templateDefinition(owner(auth), templateId));
        formOptions(model, owner(auth));
        return projectForm(model, "Create project");
    }

    @PostMapping("/projects")
    public String create(@Valid @ModelAttribute("projectForm") ProjectForm form, BindingResult binding,
            @RequestParam(required = false) UUID templateId, Authentication auth, Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", false); model.addAttribute("selectedTemplate", templateId);
            model.addAttribute("templatePreview", templateId == null ? null : projects.templateDefinition(owner(auth), templateId));
            formOptions(model, owner(auth)); return projectForm(model, "Create project");
        }
        try {
            UUID id = projects.create(owner(auth), form, templateId);
            redirect.addFlashAttribute("taskMessage", "Project created.");
            return "redirect:/projects/" + id;
        } catch (TaskRuleException ex) {
            binding.reject("project.invalid", ex.getMessage());
            model.addAttribute("editing", false); model.addAttribute("selectedTemplate", templateId);
            model.addAttribute("templatePreview", templateId == null ? null : projects.templateDefinition(owner(auth), templateId));
            formOptions(model, owner(auth)); return projectForm(model, "Create project");
        }
    }

    @GetMapping("/projects/templates")
    public String templateLibrary(@RequestParam(required = false) UUID preview, Authentication auth, Model model) {
        model.addAttribute("templates", projects.templates(owner(auth)));
        model.addAttribute("preview", preview == null ? null : projects.templateDefinition(owner(auth), preview));
        model.addAttribute("previewId", preview); model.addAttribute("activeNav", "projects");
        return page(model, "Project templates", "Reusable project structures that you own.", "projects/templates");
    }

    @GetMapping("/projects/{id}")
    public String detail(@PathVariable UUID id, Authentication auth, Model model) {
        return workspace(id, "overview", null, null, null, null, null, null, auth, model);
    }

    @GetMapping("/projects/{id}/board")
    public String board(@PathVariable UUID id, @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) TaskStatus status, @RequestParam(required = false) UUID milestoneId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) LocalDate dueBefore, @RequestParam(required = false) LocalDate dueAfter,
            Authentication auth, Model model) {
        return workspace(id, "board", priority, status, milestoneId, tagId, dueBefore, dueAfter, auth, model);
    }

    @GetMapping("/projects/{id}/milestones")
    public String milestones(@PathVariable UUID id, Authentication auth, Model model) {
        return workspace(id, "milestones", null, null, null, null, null, null, auth, model);
    }

    @GetMapping("/projects/{id}/activity")
    public String activity(@PathVariable UUID id, Authentication auth, Model model) {
        return workspace(id, "activity", null, null, null, null, null, null, auth, model);
    }

    @GetMapping("/projects/{id}/settings")
    public String settings(@PathVariable UUID id, Authentication auth, Model model) {
        return workspace(id, "settings", null, null, null, null, null, null, auth, model);
    }

    @GetMapping("/projects/{id}/edit")
    public String editForm(@PathVariable UUID id, Authentication auth, Model model) {
        Project project = projects.getProject(owner(auth), id);
        ProjectForm form = new ProjectForm();
        form.setName(project.getName()); form.setDescription(project.getDescription()); form.setStatus(project.getStatus());
        form.setStartDate(project.getStartDate()); form.setTargetEndDate(project.getTargetEndDate());
        form.setColor(project.getColor()); form.setVersion(project.getVersion());
        model.addAttribute("projectForm", form); model.addAttribute("project", project); model.addAttribute("projectId", id);
        model.addAttribute("editing", true); model.addAttribute("selectedTemplate", null); formOptions(model, owner(auth));
        return projectForm(model, "Edit project");
    }

    @PostMapping("/projects/{id}")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("projectForm") ProjectForm form,
            BindingResult binding, Authentication auth, Model model, RedirectAttributes redirect) {
        Project project = projects.getProject(owner(auth), id);
        if (binding.hasErrors()) {
            model.addAttribute("project", project); model.addAttribute("projectId", id); model.addAttribute("editing", true);
            model.addAttribute("selectedTemplate", null); formOptions(model, owner(auth)); return projectForm(model, "Edit project");
        }
        try {
            projects.update(owner(auth), id, form); redirect.addFlashAttribute("taskMessage", "Project changes saved.");
            return "redirect:/projects/" + id + "/settings";
        } catch (TaskRuleException ex) {
            binding.reject("project.invalid", ex.getMessage()); model.addAttribute("project", project);
            model.addAttribute("projectId", id); model.addAttribute("editing", true); model.addAttribute("selectedTemplate", null);
            formOptions(model, owner(auth)); return projectForm(model, "Edit project");
        }
    }

    @PostMapping("/projects/{id}/archive")
    public String archive(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.archive(owner(auth), id), "/projects/" + id + "/settings", "Project archived.");
    }

    @PostMapping("/projects/{id}/restore")
    public String restore(@PathVariable UUID id, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.restore(owner(auth), id), "/projects/" + id + "/settings", "Project restored.");
    }

    @PostMapping("/projects/{id}/delete")
    public String delete(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean confirmed,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.delete(owner(auth), id, confirmed), "/projects", "Project deleted. Its tasks remain in your task list.");
    }

    @PostMapping("/projects/{id}/tasks/add")
    public String addTask(@PathVariable UUID id, @RequestParam UUID taskId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.addExistingTask(owner(auth), id, taskId), "/projects/" + id, "Task added to the project.");
    }

    @PostMapping("/projects/{id}/tasks/create")
    public String createTask(@PathVariable UUID id, @RequestParam UUID columnId, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) LocalDate dueDate, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.createTask(owner(auth), id, columnId, title, description, priority, dueDate),
                "/projects/" + id + "/board", "Task created in the project.");
    }

    @PostMapping("/projects/{id}/tasks/{taskId}/move")
    public String moveTask(@PathVariable UUID id, @PathVariable UUID taskId, @RequestParam UUID columnId,
            @RequestParam(defaultValue = "0") int position, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.moveTask(owner(auth), id, taskId, columnId, position),
                "/projects/" + id + "/board", "Board updated.");
    }

    @PostMapping("/projects/{id}/tasks/{taskId}/remove")
    public String removeTask(@PathVariable UUID id, @PathVariable UUID taskId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.removeTask(owner(auth), id, taskId), "/projects/" + id, "Task removed from the project.");
    }

    @PostMapping("/projects/{id}/columns")
    public String createColumn(@PathVariable UUID id, @RequestParam String name, @RequestParam BoardStage stage,
            @RequestParam(required = false) Integer wipLimit, @RequestParam(defaultValue = "NONE") WipPolicy wipPolicy,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.createColumn(owner(auth), id, name, stage, wipLimit, wipPolicy),
                "/projects/" + id + "/settings", "Workflow column created.");
    }

    @PostMapping("/projects/{id}/columns/{columnId}")
    public String updateColumn(@PathVariable UUID id, @PathVariable UUID columnId, @RequestParam String name,
            @RequestParam BoardStage stage, @RequestParam(required = false) Integer wipLimit,
            @RequestParam(defaultValue = "NONE") WipPolicy wipPolicy, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.updateColumn(owner(auth), id, columnId, name, stage, wipLimit, wipPolicy),
                "/projects/" + id + "/settings", "Workflow column updated.");
    }

    @PostMapping("/projects/{id}/columns/{columnId}/order")
    public String reorderColumn(@PathVariable UUID id, @PathVariable UUID columnId, @RequestParam int direction,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.reorderColumn(owner(auth), id, columnId, direction),
                "/projects/" + id + "/settings", "Workflow order updated.");
    }

    @PostMapping("/projects/{id}/columns/{columnId}/delete")
    public String deleteColumn(@PathVariable UUID id, @PathVariable UUID columnId, @RequestParam UUID destinationColumnId,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.deleteColumn(owner(auth), id, columnId, destinationColumnId),
                "/projects/" + id + "/settings", "Workflow column removed and its tasks moved.");
    }

    @PostMapping("/projects/{id}/milestones")
    public String createMilestone(@PathVariable UUID id, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) LocalDate targetDate,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.createMilestone(owner(auth), id, title, description, targetDate),
                "/projects/" + id + "/milestones", "Milestone created.");
    }

    @PostMapping("/projects/{id}/milestones/{milestoneId}")
    public String updateMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) LocalDate targetDate,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.updateMilestone(owner(auth), id, milestoneId, title, description, targetDate),
                "/projects/" + id + "/milestones", "Milestone updated.");
    }

    @PostMapping("/projects/{id}/milestones/{milestoneId}/status")
    public String milestoneStatus(@PathVariable UUID id, @PathVariable UUID milestoneId,
            @RequestParam MilestoneStatus status, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.transitionMilestone(owner(auth), id, milestoneId, status),
                "/projects/" + id + "/milestones", "Milestone status updated.");
    }

    @PostMapping("/projects/{id}/milestones/{milestoneId}/delete")
    public String deleteMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.deleteMilestone(owner(auth), id, milestoneId),
                "/projects/" + id + "/milestones", "Milestone deleted.");
    }

    @PostMapping("/projects/{id}/tasks/{taskId}/milestone")
    public String linkMilestone(@PathVariable UUID id, @PathVariable UUID taskId,
            @RequestParam(required = false) UUID milestoneId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.linkMilestone(owner(auth), id, taskId, milestoneId),
                "/projects/" + id + "/milestones", "Task milestone updated.");
    }

    @PostMapping("/projects/{id}/templates")
    public String saveTemplate(@PathVariable UUID id, @RequestParam String name,
            @RequestParam(required = false) String description, @RequestParam(defaultValue = "false") boolean includeTasks,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.saveTemplate(owner(auth), id, name, description, includeTasks),
                "/projects/templates", "Project template saved.");
    }

    @PostMapping("/projects/templates/{templateId}/delete")
    public String deleteTemplate(@PathVariable UUID templateId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> projects.deleteTemplate(owner(auth), templateId), "/projects/templates", "Template deleted.");
    }

    private String workspace(UUID id, String view, TaskPriority priority, TaskStatus status, UUID milestoneId,
            UUID tagId, LocalDate dueBefore, LocalDate dueAfter, Authentication auth, Model model) {
        UUID owner = owner(auth); Project project = projects.getProject(owner, id);
        model.addAttribute("project", project); model.addAttribute("view", view);
        model.addAttribute("progress", projects.progress(owner, id));
        model.addAttribute("columns", projects.columns(owner, id)); model.addAttribute("milestones", projects.milestones(owner, id));
        model.addAttribute("milestoneProgress", projects.milestoneProgress(owner, id));
        model.addAttribute("activity", projects.recentActivity(owner, id));
        model.addAttribute("board", projects.board(owner, id, view.equals("board") ? priority : null,
                view.equals("board") ? status : null, view.equals("board") ? tagId : null, view.equals("board") ? milestoneId : null,
                view.equals("board") ? dueBefore : null, view.equals("board") ? dueAfter : null));
        if (view.equals("overview") || view.equals("board")) model.addAttribute("availableTasks", projects.availableTasks(owner));
        if (view.equals("board")) model.addAttribute("tags", tags.list(owner));
        model.addAttribute("priorities", TaskPriority.values()); model.addAttribute("taskStatuses", TaskStatus.values());
        model.addAttribute("stages", BoardStage.values()); model.addAttribute("wipPolicies", WipPolicy.values());
        model.addAttribute("milestoneStatuses", MilestoneStatus.values()); model.addAttribute("selectedPriority", priority);
        model.addAttribute("selectedTaskStatus", status); model.addAttribute("selectedMilestone", milestoneId);
        model.addAttribute("selectedTag", tagId);
        model.addAttribute("dueBefore", dueBefore); model.addAttribute("dueAfter", dueAfter);
        model.addAttribute("activeNav", "projects");
        return page(model, project.getName(), project.getDescription(), "projects/workspace");
    }

    private String projectForm(Model model, String title) {
        model.addAttribute("projectStatuses", ProjectStatus.values()); model.addAttribute("activeNav", "projects");
        return page(model, title, "Set the project details and choose an optional reusable template.", "projects/form");
    }

    private void formOptions(Model model, UUID owner) {
        model.addAttribute("projectStatuses", ProjectStatus.values()); model.addAttribute("templates", projects.templates(owner));
    }

    private String page(Model model, String title, String description, String view) {
        model.addAttribute("pageTitle", title + " — TASK OS"); model.addAttribute("description", description);
        model.addAttribute("activeNav", "projects"); return view;
    }

    private String action(RedirectAttributes redirect, Runnable operation, String destination, String success) {
        try { operation.run(); redirect.addFlashAttribute("taskMessage", success); }
        catch (TaskRuleException ex) { redirect.addFlashAttribute("taskError", ex.getMessage()); }
        return "redirect:" + destination;
    }

    private UUID owner(Authentication auth) { return ((TaskOsUserDetails) auth.getPrincipal()).getId(); }
}
