package com.taskos.task.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.task.dto.BulkTaskAction;
import com.taskos.task.dto.BulkTaskActionForm;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.dto.TaskSearchForm;
import com.taskos.task.dto.TaskSort;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.service.TaskConflictException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.task.service.TaskService;
import com.taskos.task.service.TaskTagService;
import com.taskos.productivity.service.ProductivityService;
import com.taskos.project.repository.ProjectTaskPlacementRepository;
import com.taskos.project.repository.ProjectMilestoneRepository;
import com.taskos.scheduling.repository.TaskScheduleMetadataRepository;
import com.taskos.scheduling.dto.DependencyLine;
import com.taskos.scheduling.service.ProjectScheduleService;
import com.taskos.time.service.FocusSessionService;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TaskController {
    private final TaskService tasks;
    private final TaskTagService tags;
    private final ProductivityService productivity;
    private final ProjectTaskPlacementRepository projectPlacements;
    private final ProjectMilestoneRepository projectMilestones;
    private final TaskScheduleMetadataRepository scheduleMetadata;
    private final ProjectScheduleService schedules;
    private final FocusSessionService focus;

    public TaskController(TaskService tasks, TaskTagService tags, ProductivityService productivity,
            ProjectTaskPlacementRepository projectPlacements, ProjectMilestoneRepository projectMilestones,
            TaskScheduleMetadataRepository scheduleMetadata, ProjectScheduleService schedules,
            FocusSessionService focus) {
        this.tasks = tasks;
        this.tags = tags;
        this.productivity = productivity;
        this.projectPlacements = projectPlacements;
        this.projectMilestones = projectMilestones;
        this.scheduleMetadata = scheduleMetadata;
        this.schedules = schedules;
        this.focus = focus;
    }

    @GetMapping("/tasks")
    public String list(@Valid @ModelAttribute("filter") TaskSearchForm filter, BindingResult binding,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(name = "selected", required = false) UUID selectedTaskId,
            Authentication authentication, Model model) {
        if (binding.hasErrors()) {
            filter = new TaskSearchForm();
            model.addAttribute("filterError", "Some filter values were invalid. Review them and try again.");
        }
        UUID ownerId = signedIn(authentication).getId();
        Page<com.taskos.task.dto.TaskListItem> tasksPage = tasks.search(ownerId, filter, page, size);
        model.addAttribute("filter", filter);
        model.addAttribute("tasksPage", tasksPage);
        model.addAttribute("tags", tags.list(ownerId));
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("priorities", TaskPriority.values());
        model.addAttribute("sortOptions", TaskSort.values());
        model.addAttribute("bulkActions", BulkTaskAction.values());
        model.addAttribute("filterView", filter.getView());
        model.addAttribute("selectedTaskId", selectedTaskId);
        model.addAttribute("workspaceReturnUrl", workspaceReturnUrl(tasksPage.getNumber(), tasksPage.getSize(), filter, selectedTaskId));
        boolean selectedInPage = selectedTaskId != null && tasksPage.getContent().stream()
                .anyMatch(item -> item.task().getId().equals(selectedTaskId));
        model.addAttribute("selectedInPage", selectedInPage);
        UUID previousTaskId = adjacentTaskId(tasksPage.getContent(), selectedTaskId, -1);
        UUID nextTaskId = adjacentTaskId(tasksPage.getContent(), selectedTaskId, 1);
        int previousPage = tasksPage.getNumber();
        int nextPage = tasksPage.getNumber();
        if (selectedInPage && previousTaskId == null && tasksPage.hasPrevious()) {
            var adjacentPage = tasks.search(ownerId, filter, tasksPage.getNumber() - 1, tasksPage.getSize());
            if (!adjacentPage.isEmpty()) {
                previousTaskId = adjacentPage.getContent().getLast().task().getId();
                previousPage = adjacentPage.getNumber();
            }
        }
        if (selectedInPage && nextTaskId == null && tasksPage.hasNext()) {
            var adjacentPage = tasks.search(ownerId, filter, tasksPage.getNumber() + 1, tasksPage.getSize());
            if (!adjacentPage.isEmpty()) {
                nextTaskId = adjacentPage.getContent().getFirst().task().getId();
                nextPage = adjacentPage.getNumber();
            }
        }
        model.addAttribute("previousTaskUrl", previousTaskId == null ? null
                : workspaceReturnUrl(previousPage, tasksPage.getSize(), filter, previousTaskId));
        model.addAttribute("nextTaskUrl", nextTaskId == null ? null
                : workspaceReturnUrl(nextPage, tasksPage.getSize(), filter, nextTaskId));
        if (selectedTaskId != null) {
            var selected = tasks.detail(ownerId, selectedTaskId);
            model.addAttribute("selectedDetail", selected);
            model.addAttribute("statuses", TaskStatus.values());
            model.addAttribute("activeFocusSession", focus.active(ownerId));
            var placement = projectPlacements.findByTaskIdAndOwnerId(selectedTaskId, ownerId);
            List<DependencyLine> selectedDependencies = placement
                    .map(item -> schedules.dependenciesForTask(ownerId, item.getProjectId(), selectedTaskId))
                    .orElse(List.of());
            model.addAttribute("selectedDependencies", selectedDependencies);
            model.addAttribute("selectedProjectId", placement.map(item -> item.getProjectId()).orElse(null));
        }
        model.addAttribute("activeNav", "COMPLETED".equals(filter.getStatus()) ? "completed" : "tasks");
        return "tasks/list";
    }

    @GetMapping("/tasks/new")
    public String createForm(Model model, Authentication authentication) {
        model.addAttribute("taskForm", new TaskForm());
        model.addAttribute("activeNav", "tasks");
        model.addAttribute("taskAction", "/tasks");
        model.addAttribute("editing", false);
        formOptions(model, signedIn(authentication).getId());
        return "tasks/form";
    }

    @PostMapping("/tasks")
    public String create(@Valid @ModelAttribute("taskForm") TaskForm form, BindingResult binding,
            Authentication authentication, Model model, RedirectAttributes redirect,
            @RequestParam(defaultValue = "details") String returnTo) {
        if (binding.hasErrors()) {
            model.addAttribute("taskAction", "/tasks");
            model.addAttribute("editing", false);
            formOptions(model, signedIn(authentication).getId());
            return "tasks/form";
        }
        try {
            UUID id = tasks.create(signedIn(authentication).getId(), form);
            redirect.addFlashAttribute("taskMessage", "Task created.");
            if ("dashboard".equals(returnTo)) return "redirect:/dashboard";
            return "redirect:/tasks/" + id;
        } catch (TaskRuleException ex) {
            binding.reject("task.invalid", ex.getMessage());
            model.addAttribute("taskAction", "/tasks");
            model.addAttribute("editing", false);
            formOptions(model, signedIn(authentication).getId());
            return "tasks/form";
        }
    }

    @GetMapping("/tasks/{id}")
    public String detail(@PathVariable UUID id, Authentication authentication, Model model) {
        UUID ownerId = signedIn(authentication).getId();
        model.addAttribute("detail", tasks.detail(ownerId, id));
        projectPlacements.findByTaskIdAndOwnerId(id, ownerId).ifPresent(placement -> {
            model.addAttribute("taskProjectId", placement.getProjectId());
            model.addAttribute("taskScheduleDurationDays", scheduleMetadata.findByTaskIdAndOwnerId(id, ownerId)
                    .map(item -> item.getPlannedDurationDays()).orElse(null));
            model.addAttribute("taskMilestoneTitle", placement.getMilestoneId() == null ? null
                    : projectMilestones.findByIdAndProjectId(placement.getMilestoneId(), placement.getProjectId())
                            .map(com.taskos.project.entity.ProjectMilestone::getTitle).orElse(null));
        });
        model.addAttribute("productivityData", productivity.taskMetadata(ownerId, id).orElse(null));
        model.addAttribute("activeNav", "tasks");
        model.addAttribute("taskId", id);
        model.addAttribute("subtaskForm", new com.taskos.task.dto.SubtaskForm());
        model.addAttribute("checklistForm", new com.taskos.task.dto.ChecklistItemForm());
        model.addAttribute("statuses", TaskStatus.values());
        return "tasks/detail";
    }

    @GetMapping("/tasks/{id}/edit")
    public String editForm(@PathVariable UUID id, Authentication authentication, Model model) {
        var detail = tasks.detail(signedIn(authentication).getId(), id);
        TaskForm form = new TaskForm();
        form.setTitle(detail.task().getTitle());
        form.setDescription(detail.task().getDescription());
        form.setStatus(detail.task().getStatus());
        form.setPriority(detail.task().getPriority());
        form.setStartDate(detail.task().getStartDate());
        form.setDueDate(detail.task().getDueDate());
        form.setTagIds(detail.task().getTags().stream().map(tag -> tag.getId()).toList());
        form.setVersion(detail.task().getVersion());
        model.addAttribute("taskForm", form);
        model.addAttribute("activeNav", "tasks");
        model.addAttribute("taskAction", "/tasks/" + id);
        model.addAttribute("taskId", id);
        model.addAttribute("taskTitle", detail.task().getTitle());
        model.addAttribute("editing", true);
        formOptions(model, signedIn(authentication).getId());
        return "tasks/form";
    }

    @PostMapping("/tasks/{id}")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("taskForm") TaskForm form,
            BindingResult binding, Authentication authentication, Model model, RedirectAttributes redirect,
            @RequestParam(required = false) String workspaceUrl) {
        UUID ownerId = signedIn(authentication).getId();
        if (binding.hasErrors()) {
            editModel(model, ownerId, id, form, false);
            return "tasks/form";
        }
        try {
            tasks.update(ownerId, id, form);
            redirect.addFlashAttribute("taskMessage", "Changes saved.");
            return com.taskos.task.service.TaskWorkspaceRedirect.afterMutation(workspaceUrl, id);
        } catch (TaskConflictException ex) {
            editModel(model, ownerId, id, form, true);
            return "tasks/form";
        } catch (TaskRuleException ex) {
            binding.reject("task.invalid", ex.getMessage());
            editModel(model, ownerId, id, form, false);
            return "tasks/form";
        }
    }

    @PostMapping("/tasks/{id}/status")
    public String status(@PathVariable UUID id, @RequestParam TaskStatus status,
            @RequestParam(defaultValue = "details") String returnTo,
            Authentication authentication, RedirectAttributes redirect,
            @RequestParam(required = false) String workspaceUrl) {
        try {
            tasks.transition(signedIn(authentication).getId(), id, status);
            redirect.addFlashAttribute("taskMessage", status == TaskStatus.COMPLETED ? "Task completed." : "Task status updated.");
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        if (workspaceUrl != null) return com.taskos.task.service.TaskWorkspaceRedirect.afterMutation(workspaceUrl, id);
        return switch (returnTo) {
            case "dashboard" -> "redirect:/dashboard";
            case "my-day" -> "redirect:/my-day";
            case "today" -> "redirect:/tasks/today";
            case "tomorrow" -> "redirect:/tasks/tomorrow";
            case "upcoming" -> "redirect:/tasks/upcoming";
            case "overdue" -> "redirect:/tasks/overdue";
            default -> "redirect:/tasks/" + id;
        };
    }

    @PostMapping("/tasks/{id}/archive")
    public String archive(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        try {
            tasks.archive(signedIn(authentication).getId(), id);
            redirect.addFlashAttribute("taskMessage", "Task archived.");
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/tasks/{id}/restore")
    public String restore(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        tasks.restore(signedIn(authentication).getId(), id);
        redirect.addFlashAttribute("taskMessage", "Task restored.");
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/tasks/{id}/delete")
    public String delete(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        tasks.delete(signedIn(authentication).getId(), id);
        redirect.addFlashAttribute("taskMessage", "Task permanently deleted.");
        return "redirect:/tasks";
    }

    @PostMapping("/tasks/bulk")
    public String bulk(@Valid @ModelAttribute("bulkForm") BulkTaskActionForm form, BindingResult binding,
            Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("taskError", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/tasks";
        }
        try {
            int count = tasks.bulk(signedIn(authentication).getId(), form);
            redirect.addFlashAttribute("taskMessage", "Applied the action to " + count + " selected task(s).");
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return "redirect:/tasks";
    }

    private void editModel(Model model, UUID ownerId, UUID id, TaskForm form, boolean conflict) {
        var detail = tasks.detail(ownerId, id);
        model.addAttribute("taskForm", form);
        model.addAttribute("activeNav", "tasks");
        model.addAttribute("taskAction", "/tasks/" + id);
        model.addAttribute("taskId", id);
        model.addAttribute("taskTitle", detail.task().getTitle());
        model.addAttribute("editing", true);
        model.addAttribute("conflict", conflict);
        formOptions(model, ownerId);
    }

    private void formOptions(Model model, UUID ownerId) {
        model.addAttribute("tags", tags.list(ownerId));
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("priorities", TaskPriority.values());
    }

    private String workspaceReturnUrl(int page, int size, TaskSearchForm filter, UUID selectedId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/tasks")
                .queryParam("page", page).queryParam("size", size).queryParam("q", filter.getQ())
                .queryParam("status", filter.getStatus()).queryParam("priority", filter.getPriority())
                .queryParam("view", filter.getView()).queryParam("due", filter.getDue())
                .queryParam("subtasks", filter.getSubtasks()).queryParam("sort", filter.getSort());
        if (filter.getTagId() != null) builder.queryParam("tagId", filter.getTagId());
        if (selectedId != null) builder.queryParam("selected", selectedId);
        return builder.build().encode().toUriString();
    }

    private UUID adjacentTaskId(List<com.taskos.task.dto.TaskListItem> items, UUID selectedId, int offset) {
        if (selectedId == null) return null;
        int index = java.util.stream.IntStream.range(0, items.size())
                .filter(i -> items.get(i).task().getId().equals(selectedId)).findFirst().orElse(-1);
        int adjacent = index + offset;
        return index < 0 || adjacent < 0 || adjacent >= items.size() ? null : items.get(adjacent).task().getId();
    }

    private TaskOsUserDetails signedIn(Authentication authentication) {
        return (TaskOsUserDetails) authentication.getPrincipal();
    }
}
