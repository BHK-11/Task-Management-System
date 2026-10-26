package com.taskos.collaboration.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.collaboration.entity.Workspace;
import com.taskos.collaboration.entity.WorkspaceMembership;
import com.taskos.collaboration.entity.WorkspaceNotification;
import com.taskos.collaboration.entity.WorkspaceRole;
import com.taskos.collaboration.service.WorkspaceAuthorizationService;
import com.taskos.collaboration.service.WorkspacePermission;
import com.taskos.collaboration.service.WorkspaceProjectService;
import com.taskos.collaboration.service.WorkspaceService;
import com.taskos.collaboration.service.WorkspaceTaskService;
import com.taskos.project.entity.Project;
import com.taskos.project.entity.ProjectTaskPlacement;
import com.taskos.project.service.ProjectService;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.service.TaskRuleException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WorkspaceTaskController {
    private final WorkspaceService workspaces;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceTaskService tasks;
    private final WorkspaceProjectService projects;
    private final ProjectService projectService;

    public WorkspaceTaskController(WorkspaceService workspaces, WorkspaceAuthorizationService authorization,
            WorkspaceTaskService tasks, WorkspaceProjectService projects, ProjectService projectService) {
        this.workspaces = workspaces; this.authorization = authorization; this.tasks = tasks;
        this.projects = projects; this.projectService = projectService;
    }

    @PostMapping("/workspaces/{workspaceId}/tasks")
    public String create(@PathVariable UUID workspaceId, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) java.time.LocalDate dueDate, Authentication auth, RedirectAttributes redirect) {
        try {
            UUID taskId = tasks.create(userId(auth), workspaceId, title, description, priority, dueDate);
            redirect.addFlashAttribute("taskMessage", "Shared task created.");
            return "redirect:/workspaces/" + workspaceId + "/tasks/" + taskId;
        } catch (TaskRuleException ex) { redirect.addFlashAttribute("taskError", ex.getMessage()); return "redirect:/workspaces/" + workspaceId; }
    }

    @GetMapping("/workspaces/{workspaceId}/tasks/{taskId}")
    public String detail(@PathVariable UUID workspaceId, @PathVariable UUID taskId,
            Authentication auth, Model model) {
        UUID actorId = userId(auth);
        Workspace workspace = workspaces.workspace(actorId, workspaceId);
        WorkspaceMembership current = authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        Task task = tasks.task(actorId, workspaceId, taskId);
        model.addAttribute("workspace", workspace); model.addAttribute("task", task);
        model.addAttribute("members", workspaces.members(actorId, workspaceId));
        model.addAttribute("assignee", tasks.assignee(actorId, workspaceId, taskId));
        model.addAttribute("comments", tasks.comments(actorId, workspaceId, taskId));
        model.addAttribute("statuses", List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.ON_HOLD,
                TaskStatus.COMPLETED, TaskStatus.CANCELLED));
        model.addAttribute("priorities", TaskPriority.values());
        model.addAttribute("canEdit", current.getRole() != WorkspaceRole.VIEWER);
        model.addAttribute("currentUserId", actorId);
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", task.getTitle() + " — " + workspace.getName());
        return "teams/task";
    }

    @PostMapping("/workspaces/{workspaceId}/tasks/{taskId}/assignment")
    public String assign(@PathVariable UUID workspaceId, @PathVariable UUID taskId,
            @RequestParam(required = false) UUID assigneeId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> tasks.assign(userId(auth), workspaceId, taskId, assigneeId),
                "/workspaces/" + workspaceId + "/tasks/" + taskId, "Task assignment updated.");
    }

    @PostMapping("/workspaces/{workspaceId}/tasks/{taskId}/status")
    public String status(@PathVariable UUID workspaceId, @PathVariable UUID taskId,
            @RequestParam TaskStatus status, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> tasks.changeStatus(userId(auth), workspaceId, taskId, status),
                "/workspaces/" + workspaceId + "/tasks/" + taskId, "Task status updated.");
    }

    @PostMapping("/workspaces/{workspaceId}/tasks/{taskId}/comments")
    public String comment(@PathVariable UUID workspaceId, @PathVariable UUID taskId, @RequestParam String body,
            @RequestParam(required = false) List<UUID> mentionIds, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> tasks.comment(userId(auth), workspaceId, taskId, body, mentionIds),
                "/workspaces/" + workspaceId + "/tasks/" + taskId, "Comment added.");
    }

    @PostMapping("/workspaces/{workspaceId}/tasks/{taskId}/comments/{commentId}/edit")
    public String editComment(@PathVariable UUID workspaceId, @PathVariable UUID taskId, @PathVariable UUID commentId,
            @RequestParam String body, @RequestParam(required = false) List<UUID> mentionIds,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> tasks.editComment(userId(auth), workspaceId, taskId, commentId, body, mentionIds),
                "/workspaces/" + workspaceId + "/tasks/" + taskId, "Comment updated.");
    }

    @PostMapping("/workspaces/{workspaceId}/tasks/{taskId}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable UUID workspaceId, @PathVariable UUID taskId, @PathVariable UUID commentId,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> tasks.deleteComment(userId(auth), workspaceId, taskId, commentId),
                "/workspaces/" + workspaceId + "/tasks/" + taskId, "Comment deleted.");
    }

    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}")
    public String sharedProject(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            Authentication auth, Model model) {
        UUID actorId = userId(auth);
        Workspace workspace = workspaces.workspace(actorId, workspaceId);
        Project project = projects.sharedProject(actorId, workspaceId, projectId);
        WorkspaceMembership member = authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        var board = projectService.sharedBoard(actorId, projectId);
        model.addAttribute("workspace", workspace); model.addAttribute("project", project);
        model.addAttribute("board", board);
        model.addAttribute("todoColumns", board.stream().map(com.taskos.project.dto.ProjectBoardColumnView::column)
                .filter(column -> column.getStage().taskStatus() == TaskStatus.TODO).toList());
        model.addAttribute("canEdit", workspace.getStatus().name().equals("ACTIVE")
                && project.getArchivedAt() == null && member.getRole() != WorkspaceRole.VIEWER);
        model.addAttribute("activeNav", "teams");
        model.addAttribute("pageTitle", project.getName() + " — " + workspace.getName());
        return "teams/project";
    }

    @PostMapping("/workspaces/{workspaceId}/projects/{projectId}/tasks")
    public String createProjectTask(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @RequestParam UUID columnId, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) java.time.LocalDate dueDate, Authentication auth, RedirectAttributes redirect) {
        UUID actorId = userId(auth);
        return action(redirect, () -> {
            projects.sharedProject(actorId, workspaceId, projectId);
            projectService.createSharedProjectTask(actorId, projectId, columnId, title, description, priority, dueDate);
        }, "/workspaces/" + workspaceId + "/projects/" + projectId, "Task added to the shared project.");
    }

    @PostMapping("/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/move")
    public String moveProjectTask(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @RequestParam UUID columnId, @RequestParam(defaultValue = "0") int position,
            Authentication auth, RedirectAttributes redirect) {
        UUID actorId = userId(auth);
        return action(redirect, () -> {
            projects.sharedProject(actorId, workspaceId, projectId);
            projectService.moveTaskInSharedProject(actorId, projectId, taskId, columnId, position);
        }, "/workspaces/" + workspaceId + "/projects/" + projectId, "Shared project board updated.");
    }

    @GetMapping("/assigned-to-me")
    public String assignedToMe(Authentication auth, Model model) {
        model.addAttribute("assignedTasks", tasks.assignedToMe(userId(auth)));
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", "Assigned to me — TASK OS");
        return "teams/assigned";
    }

    @GetMapping("/notifications")
    public String notificationCenter(Authentication auth, Model model) {
        model.addAttribute("notifications", tasks.notifications(userId(auth)));
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", "Notifications — TASK OS");
        return "teams/notifications";
    }

    @PostMapping("/notifications/{notificationId}/read")
    public String markRead(@PathVariable UUID notificationId, Authentication auth, RedirectAttributes redirect) {
        tasks.markNotificationRead(userId(auth), notificationId);
        redirect.addFlashAttribute("taskMessage", "Notification marked as read.");
        return "redirect:/notifications";
    }

    private static UUID userId(Authentication auth) { return ((TaskOsUserDetails) auth.getPrincipal()).getId(); }

    private static String action(RedirectAttributes redirect, Runnable operation, String destination, String success) {
        try { operation.run(); redirect.addFlashAttribute("taskMessage", success); }
        catch (TaskRuleException ex) { redirect.addFlashAttribute("taskError", ex.getMessage()); }
        return "redirect:" + destination;
    }
}
