package com.taskos.task.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.task.dto.ChecklistItemForm;
import com.taskos.task.dto.SubtaskForm;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.service.ChecklistService;
import com.taskos.task.service.TaskRuleException;
import com.taskos.task.service.TaskService;
import com.taskos.task.service.TaskWorkspaceRedirect;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TaskComponentController {
    private final TaskService tasks;
    private final ChecklistService checklist;

    public TaskComponentController(TaskService tasks, ChecklistService checklist) {
        this.tasks = tasks;
        this.checklist = checklist;
    }

    @PostMapping("/tasks/{taskId}/subtasks")
    public String addSubtask(@PathVariable UUID taskId, @Valid @ModelAttribute("subtaskForm") SubtaskForm form,
            BindingResult binding, Authentication authentication, Model model, RedirectAttributes redirect,
            @RequestParam(required = false) String workspaceUrl) {
        UUID ownerId = signedIn(authentication).getId();
        if (binding.hasErrors()) {
            if (workspaceUrl != null) {
                redirect.addFlashAttribute("taskError", "Check the subtask title and priority.");
                return TaskWorkspaceRedirect.afterMutation(workspaceUrl, taskId);
            }
            return detail(ownerId, taskId, model, form, new ChecklistItemForm(), binding, null);
        }
        try {
            UUID id = tasks.createSubtask(ownerId, taskId, form);
            redirect.addFlashAttribute("taskMessage", "Subtask added.");
            return TaskWorkspaceRedirect.afterMutation(workspaceUrl, taskId);
        } catch (TaskRuleException ex) {
            if (workspaceUrl != null) {
                redirect.addFlashAttribute("taskError", ex.getMessage());
                return TaskWorkspaceRedirect.afterMutation(workspaceUrl, taskId);
            }
            binding.reject("subtask.invalid", ex.getMessage());
            return detail(ownerId, taskId, model, form, new ChecklistItemForm(), binding, null);
        }
    }

    @PostMapping("/tasks/{taskId}/subtasks/{subtaskId}")
    public String updateSubtask(@PathVariable UUID taskId, @PathVariable UUID subtaskId,
            @Valid @ModelAttribute("subtaskForm") SubtaskForm form, BindingResult binding,
            Authentication authentication, RedirectAttributes redirect,
            @RequestParam(required = false) String workspaceUrl) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("taskError", "Check the subtask title and priority.");
            return TaskWorkspaceRedirect.afterMutation(workspaceUrl, taskId);
        }
        try {
            tasks.updateSubtask(signedIn(authentication).getId(), taskId, subtaskId, form);
            redirect.addFlashAttribute("taskMessage", "Subtask updated.");
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return TaskWorkspaceRedirect.afterMutation(workspaceUrl, taskId);
    }

    @PostMapping("/tasks/{taskId}/subtasks/{subtaskId}/status")
    public String subtaskStatus(@PathVariable UUID taskId, @PathVariable UUID subtaskId,
            @RequestParam TaskStatus status, Authentication authentication, RedirectAttributes redirect,
            @RequestParam(required = false) String workspaceUrl) {
        try {
            tasks.transitionSubtask(signedIn(authentication).getId(), taskId, subtaskId, status);
            redirect.addFlashAttribute("taskMessage", "Subtask status updated.");
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return TaskWorkspaceRedirect.afterMutation(workspaceUrl, taskId);
    }

    @PostMapping("/tasks/{taskId}/subtasks/{subtaskId}/toggle")
    public ResponseEntity<?> toggleSubtask(@PathVariable UUID taskId, @PathVariable UUID subtaskId,
            @RequestParam TaskStatus status, Authentication authentication) {
        try {
            UUID ownerId = signedIn(authentication).getId();
            tasks.transitionSubtask(ownerId, taskId, subtaskId, status);
            var detail = tasks.detail(ownerId, taskId);
            long total = detail.subtasks().size();
            long completed = detail.completedSubtaskCount();
            int percent = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
            return ResponseEntity.ok(java.util.Map.of("completed", completed, "total", total, "percent", percent));
        } catch (TaskRuleException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/tasks/{taskId}/subtasks/{subtaskId}/move")
    public String moveSubtask(@PathVariable UUID taskId, @PathVariable UUID subtaskId,
            @RequestParam String direction, Authentication authentication, RedirectAttributes redirect,
            @RequestParam(required = false) String workspaceUrl) {
        try {
            tasks.moveSubtask(signedIn(authentication).getId(), taskId, subtaskId, direction);
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return TaskWorkspaceRedirect.afterMutation(workspaceUrl, taskId);
    }

    @PostMapping("/tasks/{taskId}/subtasks/{subtaskId}/delete")
    public String deleteSubtask(@PathVariable UUID taskId, @PathVariable UUID subtaskId,
            Authentication authentication, RedirectAttributes redirect,
            @RequestParam(required = false) String workspaceUrl) {
        try {
            tasks.deleteSubtask(signedIn(authentication).getId(), taskId, subtaskId);
            redirect.addFlashAttribute("taskMessage", "Subtask deleted.");
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return TaskWorkspaceRedirect.afterMutation(workspaceUrl, taskId);
    }

    @PostMapping("/tasks/{taskId}/checklist")
    public String addChecklist(@PathVariable UUID taskId,
            @Valid @ModelAttribute("checklistForm") ChecklistItemForm form, BindingResult binding,
            Authentication authentication, Model model, RedirectAttributes redirect) {
        UUID ownerId = signedIn(authentication).getId();
        if (binding.hasErrors()) return detail(ownerId, taskId, model, new SubtaskForm(), form, null, binding);
        try {
            checklist.add(ownerId, taskId, form);
            redirect.addFlashAttribute("taskMessage", "Checklist item added.");
            return "redirect:/tasks/" + taskId;
        } catch (TaskRuleException ex) {
            binding.reject("checklist.invalid", ex.getMessage());
            return detail(ownerId, taskId, model, new SubtaskForm(), form, null, binding);
        }
    }

    @PostMapping("/tasks/{taskId}/checklist/{itemId}")
    public String updateChecklist(@PathVariable UUID taskId, @PathVariable UUID itemId,
            @Valid @ModelAttribute("checklistForm") ChecklistItemForm form, BindingResult binding,
            Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("taskError", "Check the checklist item and try again.");
            return "redirect:/tasks/" + taskId;
        }
        try {
            checklist.update(signedIn(authentication).getId(), taskId, itemId, form);
            redirect.addFlashAttribute("taskMessage", "Checklist item updated.");
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/tasks/{taskId}/checklist/{itemId}/toggle")
    public String toggleChecklist(@PathVariable UUID taskId, @PathVariable UUID itemId,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            checklist.toggle(signedIn(authentication).getId(), taskId, itemId);
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/tasks/{taskId}/checklist/{itemId}/move")
    public String moveChecklist(@PathVariable UUID taskId, @PathVariable UUID itemId,
            @RequestParam String direction, Authentication authentication, RedirectAttributes redirect) {
        try {
            checklist.move(signedIn(authentication).getId(), taskId, itemId, direction);
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/tasks/{taskId}/checklist/{itemId}/delete")
    public String deleteChecklist(@PathVariable UUID taskId, @PathVariable UUID itemId,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            checklist.delete(signedIn(authentication).getId(), taskId, itemId);
            redirect.addFlashAttribute("taskMessage", "Checklist item deleted.");
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    private String detail(UUID ownerId, UUID taskId, Model model, SubtaskForm subtaskForm,
            ChecklistItemForm checklistForm, BindingResult subtaskErrors, BindingResult checklistErrors) {
        model.addAttribute("detail", tasks.detail(ownerId, taskId));
        model.addAttribute("activeNav", "tasks");
        model.addAttribute("taskId", taskId);
        model.addAttribute("subtaskForm", subtaskForm);
        model.addAttribute("checklistForm", checklistForm);
        model.addAttribute("statuses", TaskStatus.values());
        if (subtaskErrors != null) model.addAttribute("org.springframework.validation.BindingResult.subtaskForm", subtaskErrors);
        if (checklistErrors != null) model.addAttribute("org.springframework.validation.BindingResult.checklistForm", checklistErrors);
        return "tasks/detail";
    }

    private TaskOsUserDetails signedIn(Authentication authentication) {
        return (TaskOsUserDetails) authentication.getPrincipal();
    }
}
