package com.taskos.collaboration.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.collaboration.entity.MembershipStatus;
import com.taskos.collaboration.entity.WorkspaceActivity;
import com.taskos.collaboration.entity.WorkspaceInvitation;
import com.taskos.collaboration.entity.WorkspaceMembership;
import com.taskos.collaboration.entity.WorkspaceNotification;
import com.taskos.collaboration.entity.WorkspaceRole;
import com.taskos.collaboration.service.WorkspaceAuthorizationService;
import com.taskos.collaboration.service.WorkspaceInvitationService;
import com.taskos.collaboration.service.WorkspacePermission;
import com.taskos.collaboration.service.WorkspaceProjectService;
import com.taskos.collaboration.service.WorkspaceService;
import com.taskos.collaboration.service.WorkspaceTaskService;
import com.taskos.collaboration.dto.WorkloadMember;
import com.taskos.collaboration.entity.Workspace;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.service.TaskRuleException;
import com.taskos.user.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WorkspaceController {
    private final WorkspaceService workspaces;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceInvitationService invitations;
    private final WorkspaceProjectService projects;
    private final WorkspaceTaskService tasks;
    private final UserAccountRepository users;

    public WorkspaceController(WorkspaceService workspaces, WorkspaceAuthorizationService authorization,
            WorkspaceInvitationService invitations, WorkspaceProjectService projects,
            WorkspaceTaskService tasks, UserAccountRepository users) {
        this.workspaces = workspaces; this.authorization = authorization; this.invitations = invitations;
        this.projects = projects; this.tasks = tasks; this.users = users;
    }

    @GetMapping("/teams")
    public String hub(Authentication auth, Model model) {
        model.addAttribute("workspaces", workspaces.list(userId(auth)));
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", "Team workspaces — TASK OS");
        return "teams/index";
    }

    @GetMapping("/workspaces")
    public String workspaceHub() { return "redirect:/teams"; }

    @GetMapping("/workspaces/new")
    public String createForm(Model model) {
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", "Create a workspace — TASK OS");
        return "teams/new";
    }

    @PostMapping("/workspaces")
    public String create(@RequestParam String name, @RequestParam(required = false) String description,
            Authentication auth, RedirectAttributes redirect) {
        try {
            UUID id = workspaces.create(userId(auth), name, description);
            redirect.addFlashAttribute("taskMessage", "Workspace created. You are its owner.");
            return "redirect:/workspaces/" + id;
        } catch (TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
            return "redirect:/workspaces/new";
        }
    }

    @GetMapping("/workspaces/{workspaceId}")
    public String detail(@PathVariable UUID workspaceId, Authentication auth, Model model) {
        UUID actorId = userId(auth);
        Workspace workspace = workspaces.workspace(actorId, workspaceId);
        WorkspaceMembership current = authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        List<WorkspaceActivity> activity = workspaces.activities(actorId, workspaceId);
        Set<UUID> actorIds = activity.stream().map(WorkspaceActivity::getActorId).collect(Collectors.toSet());
        Map<UUID, String> actorNames = users.findAllById(actorIds).stream()
                .collect(Collectors.toMap(user -> user.getId(), user -> user.getDisplayName()));
        List<WorkspaceNotification> notifications = tasks.notifications(actorId).stream()
                .filter(item -> item.getWorkspaceId().equals(workspaceId)).limit(8).toList();
        model.addAttribute("workspace", workspace);
        model.addAttribute("currentMembership", current);
        model.addAttribute("members", workspaces.members(actorId, workspaceId));
        model.addAttribute("projects", projects.sharedProjects(actorId, workspaceId));
        model.addAttribute("shareableProjects", current.getRole() == WorkspaceRole.OWNER || current.getRole() == WorkspaceRole.ADMIN
                ? projects.shareableProjects(actorId, workspaceId) : List.of());
        model.addAttribute("tasks", tasks.tasks(actorId, workspaceId));
        model.addAttribute("activities", activity); model.addAttribute("activityActors", actorNames);
        model.addAttribute("notifications", notifications); model.addAttribute("unassignedCount", tasks.unassigned(actorId, workspaceId));
        model.addAttribute("canEdit", current.getRole() != WorkspaceRole.VIEWER);
        model.addAttribute("canManage", current.getRole() == WorkspaceRole.OWNER || current.getRole() == WorkspaceRole.ADMIN);
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", workspace.getName() + " — TASK OS");
        return "teams/detail";
    }

    @GetMapping("/workspaces/{workspaceId}/calendar")
    public String calendar(@PathVariable UUID workspaceId, @RequestParam(required = false) LocalDate month,
            Authentication auth, Model model) {
        UUID actorId = userId(auth);
        Workspace workspace = workspaces.workspace(actorId, workspaceId);
        model.addAttribute("workspace", workspace);
        model.addAttribute("calendar", tasks.calendar(actorId, workspaceId, month));
        model.addAttribute("activeNav", "teams");
        model.addAttribute("pageTitle", "Team calendar — " + workspace.getName());
        return "teams/calendar";
    }

    @GetMapping("/workspaces/{workspaceId}/members")
    public String members(@PathVariable UUID workspaceId, Authentication auth, Model model) {
        UUID actorId = userId(auth);
        Workspace workspace = workspaces.workspace(actorId, workspaceId);
        WorkspaceMembership current = authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        boolean canManage = current.getRole() == WorkspaceRole.OWNER || current.getRole() == WorkspaceRole.ADMIN;
        boolean canOwn = current.getRole() == WorkspaceRole.OWNER;
        model.addAttribute("workspace", workspace); model.addAttribute("currentMembership", current);
        model.addAttribute("members", workspaces.members(actorId, workspaceId));
        model.addAttribute("invitations", canManage ? invitations.list(actorId, workspaceId) : List.of());
        model.addAttribute("roles", canOwn ? List.of(WorkspaceRole.ADMIN, WorkspaceRole.MEMBER, WorkspaceRole.VIEWER)
                : List.of(WorkspaceRole.MEMBER, WorkspaceRole.VIEWER));
        model.addAttribute("canManage", canManage); model.addAttribute("canOwn", canOwn);
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", "Members — " + workspace.getName());
        return "teams/members";
    }

    @PostMapping("/workspaces/{workspaceId}/settings")
    public String update(@PathVariable UUID workspaceId, @RequestParam String name,
            @RequestParam(required = false) String description, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> workspaces.update(userId(auth), workspaceId, name, description),
                "/workspaces/" + workspaceId, "Workspace settings saved.");
    }

    @PostMapping("/workspaces/{workspaceId}/archive")
    public String archive(@PathVariable UUID workspaceId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> workspaces.archive(userId(auth), workspaceId),
                "/workspaces/" + workspaceId, "Workspace archived. Its history is preserved.");
    }

    @PostMapping("/workspaces/{workspaceId}/restore")
    public String restore(@PathVariable UUID workspaceId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> workspaces.restore(userId(auth), workspaceId),
                "/workspaces/" + workspaceId, "Workspace restored.");
    }

    @PostMapping("/workspaces/{workspaceId}/members/{memberId}/role")
    public String changeRole(@PathVariable UUID workspaceId, @PathVariable UUID memberId,
            @RequestParam WorkspaceRole role, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> workspaces.changeRole(userId(auth), workspaceId, memberId, role),
                "/workspaces/" + workspaceId + "/members", "Member role updated.");
    }

    @PostMapping("/workspaces/{workspaceId}/members/{memberId}/remove")
    public String removeMember(@PathVariable UUID workspaceId, @PathVariable UUID memberId,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> workspaces.removeMember(userId(auth), workspaceId, memberId),
                "/workspaces/" + workspaceId + "/members", "Member removed and their open assignments cleared.");
    }

    @PostMapping("/workspaces/{workspaceId}/members/{memberId}/capacity")
    public String capacity(@PathVariable UUID workspaceId, @PathVariable UUID memberId,
            @RequestParam(required = false) Integer maxOpenTasks, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> workspaces.setCapacity(userId(auth), workspaceId, memberId, maxOpenTasks),
                "/workspaces/" + workspaceId + "/workload", "Task-count limit saved.");
    }

    @PostMapping("/workspaces/{workspaceId}/notifications/preferences")
    public String notificationPreferences(@PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean notifyAssignments,
            @RequestParam(defaultValue = "false") boolean notifyMentions,
            @RequestParam(defaultValue = "false") boolean notifyActivity,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> workspaces.setNotificationPreferences(userId(auth), workspaceId,
                notifyAssignments, notifyMentions, notifyActivity),
                "/workspaces/" + workspaceId + "/members", "Notification preferences saved.");
    }

    @PostMapping("/workspaces/{workspaceId}/members/{memberId}/transfer-ownership")
    public String transferOwnership(@PathVariable UUID workspaceId, @PathVariable UUID memberId,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> workspaces.transferOwnership(userId(auth), workspaceId, memberId),
                "/workspaces/" + workspaceId + "/members", "Workspace ownership transferred.");
    }

    @PostMapping("/workspaces/{workspaceId}/invitations")
    public String invite(@PathVariable UUID workspaceId, @RequestParam String email,
            @RequestParam WorkspaceRole role, Authentication auth, RedirectAttributes redirect) {
        try {
            String token = invitations.create(userId(auth), workspaceId, email, role);
            redirect.addFlashAttribute("inviteLink", "/invitations/" + token);
            redirect.addFlashAttribute("taskMessage", "Invitation created. No email was sent; copy the secure link below.");
        } catch (TaskRuleException ex) { redirect.addFlashAttribute("taskError", ex.getMessage()); }
        return "redirect:/workspaces/" + workspaceId + "/members";
    }

    @PostMapping("/workspaces/{workspaceId}/invitations/{invitationId}/revoke")
    public String revokeInvitation(@PathVariable UUID workspaceId, @PathVariable UUID invitationId,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> invitations.revoke(userId(auth), workspaceId, invitationId),
                "/workspaces/" + workspaceId + "/members", "Invitation revoked.");
    }

    @GetMapping("/invitations/{token}")
    public String invitePreview(@PathVariable String token, Authentication auth, Model model, HttpServletResponse response) {
        response.setHeader("Referrer-Policy", "no-referrer"); response.setHeader("Cache-Control", "no-store");
        TaskOsUserDetails user = signedIn(auth);
        model.addAttribute("token", token); model.addAttribute("invite", invitations.inspect(token, user.getEmail()));
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", "Workspace invitation — TASK OS");
        return "teams/invitation";
    }

    @PostMapping("/invitations/{token}/accept")
    public String acceptInvite(@PathVariable String token, Authentication auth, RedirectAttributes redirect,
            HttpServletResponse response) {
        response.setHeader("Referrer-Policy", "no-referrer"); response.setHeader("Cache-Control", "no-store");
        UUID workspaceId = invitations.accept(token, signedIn(auth).getId(), signedIn(auth).getEmail());
        redirect.addFlashAttribute("taskMessage", "You joined the workspace.");
        return "redirect:/workspaces/" + workspaceId;
    }

    @PostMapping("/invitations/{token}/decline")
    public String declineInvite(@PathVariable String token, Authentication auth, RedirectAttributes redirect,
            HttpServletResponse response) {
        response.setHeader("Referrer-Policy", "no-referrer"); response.setHeader("Cache-Control", "no-store");
        TaskOsUserDetails user = signedIn(auth); invitations.decline(token, user.getId(), user.getEmail());
        redirect.addFlashAttribute("taskMessage", "Invitation declined."); return "redirect:/teams";
    }

    @GetMapping("/workspaces/{workspaceId}/workload")
    public String workload(@PathVariable UUID workspaceId, Authentication auth, Model model) {
        UUID actorId = userId(auth);
        Workspace workspace = workspaces.workspace(actorId, workspaceId);
        List<WorkloadMember> members = tasks.workload(actorId, workspaceId);
        WorkspaceMembership current = authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        model.addAttribute("workspace", workspace); model.addAttribute("workload", members);
        model.addAttribute("canManage", current.getRole() == WorkspaceRole.OWNER || current.getRole() == WorkspaceRole.ADMIN);
        model.addAttribute("ownUserId", actorId); model.addAttribute("unassignedCount", tasks.unassigned(actorId, workspaceId));
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", "Workload — " + workspace.getName());
        return "teams/workload";
    }

    private static TaskOsUserDetails signedIn(Authentication authentication) { return (TaskOsUserDetails) authentication.getPrincipal(); }
    private static UUID userId(Authentication authentication) { return signedIn(authentication).getId(); }

    private static String action(RedirectAttributes redirect, Runnable operation, String destination, String success) {
        try { operation.run(); redirect.addFlashAttribute("taskMessage", success); }
        catch (TaskRuleException ex) { redirect.addFlashAttribute("taskError", ex.getMessage()); }
        return "redirect:" + destination;
    }
}
