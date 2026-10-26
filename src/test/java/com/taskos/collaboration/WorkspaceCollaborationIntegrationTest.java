package com.taskos.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskos.auth.service.RegistrationService;
import com.taskos.agile.service.AgileService;
import com.taskos.collaboration.entity.MembershipStatus;
import com.taskos.collaboration.entity.WorkspaceNotificationType;
import com.taskos.collaboration.entity.WorkspaceRole;
import com.taskos.collaboration.repository.WorkspaceMembershipRepository;
import com.taskos.collaboration.service.WorkspaceForbiddenException;
import com.taskos.collaboration.service.WorkspaceInvitationService;
import com.taskos.collaboration.service.WorkspaceProjectService;
import com.taskos.collaboration.service.WorkspaceService;
import com.taskos.collaboration.service.WorkspaceTaskService;
import com.taskos.project.dto.ProjectForm;
import com.taskos.project.entity.BoardStage;
import com.taskos.project.entity.ProjectStatus;
import com.taskos.project.repository.BoardColumnRepository;
import com.taskos.project.repository.ProjectTaskPlacementRepository;
import com.taskos.project.service.ProjectService;
import com.taskos.scheduling.entity.TaskDependencyType;
import com.taskos.scheduling.repository.TaskDependencyRepository;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.user.entity.UserAccount;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkspaceCollaborationIntegrationTest {
    private static final String PASSWORD = "a sufficiently long test passphrase";

    @Autowired private RegistrationService registration;
    @Autowired private DatabaseUserDetailsService userDetails;
    @Autowired private WorkspaceService workspaces;
    @Autowired private WorkspaceInvitationService invitations;
    @Autowired private WorkspaceTaskService sharedTasks;
    @Autowired private WorkspaceProjectService sharedProjects;
    @Autowired private AgileService agile;
    @Autowired private TaskDependencyRepository dependencies;
    @Autowired private WorkspaceMembershipRepository memberships;
    @Autowired private ProjectService projects;
    @Autowired private BoardColumnRepository columns;
    @Autowired private ProjectTaskPlacementRepository placements;
    @Autowired private TaskRepository tasks;
    @Autowired private MockMvc mvc;

    @Test
    void invitationsRequireTheIntendedAccountAndCanOnlyBeAcceptedOnce() {
        UserAccount owner = register("Owner");
        UserAccount invited = register("Invited");
        UserAccount other = register("Other");
        UUID workspaceId = workspaces.create(owner.getId(), "Invite boundary", "");
        String token = invitations.create(owner.getId(), workspaceId, invited.getEmail(), WorkspaceRole.MEMBER);

        assertEquals(43, token.length());
        assertThrows(TaskNotFoundException.class, () -> invitations.inspect(token, other.getEmail()));
        assertThrows(TaskNotFoundException.class, () -> invitations.accept(token, other.getId(), other.getEmail()));

        assertEquals(workspaceId, invitations.accept(token, invited.getId(), invited.getEmail()));
        assertEquals(WorkspaceRole.MEMBER, memberships.findByWorkspace_IdAndUser_Id(workspaceId, invited.getId()).orElseThrow().getRole());
        assertThrows(TaskRuleException.class, () -> invitations.accept(token, invited.getId(), invited.getEmail()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentInvitationAcceptanceCreatesOnlyOneMembership() throws Exception {
        UserAccount owner = register("Concurrent invite owner");
        UserAccount invitee = register("Concurrent invitee");
        UUID workspaceId = workspaces.create(owner.getId(), "Concurrent acceptance", "");
        String token = invitations.create(owner.getId(), workspaceId, invitee.getEmail(), WorkspaceRole.MEMBER);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var accept = (java.util.concurrent.Callable<Boolean>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Acceptance barrier timed out.");
                try { invitations.accept(token, invitee.getId(), invitee.getEmail()); return true; }
                catch (TaskRuleException | TaskNotFoundException rejected) { return false; }
            };
            var first = executor.submit(accept);
            var second = executor.submit(accept);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(1, memberships.findAllByWorkspace_IdAndStatusOrderByJoinedAtAsc(workspaceId, MembershipStatus.ACTIVE)
                .stream().filter(row -> row.getUser().getId().equals(invitee.getId())).count());
    }

    @Test
    void workspaceRolesAndTenantScopeProtectSharedTasksAndNotifications() throws Exception {
        UserAccount owner = register("Owner");
        UserAccount member = register("Member");
        UserAccount viewer = register("Viewer");
        UserAccount otherOwner = register("Other owner");
        UUID workspaceId = workspaces.create(owner.getId(), "Alpha", "");
        UUID otherWorkspaceId = workspaces.create(otherOwner.getId(), "Beta", "");
        acceptInvite(owner, workspaceId, member, WorkspaceRole.MEMBER);
        acceptInvite(owner, workspaceId, viewer, WorkspaceRole.VIEWER);
        workspaces.setNotificationPreferences(viewer.getId(), workspaceId, true, true, true);
        UUID taskId = sharedTasks.create(owner.getId(), workspaceId, "Shared task", "", TaskPriority.HIGH, null);

        assertTrue(tasks.findDetailByIdAndOwner(taskId, owner.getId()).isEmpty(), "shared tasks must stay out of personal task queries");
        assertThrows(TaskNotFoundException.class, () -> sharedTasks.task(otherOwner.getId(), otherWorkspaceId, taskId));
        mvc.perform(get("/workspaces/{workspaceId}/tasks/{taskId}", otherWorkspaceId, taskId)
                        .with(user(userDetails.loadUserByUsername(otherOwner.getEmail()))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/tasks/{taskId}", taskId).with(user(userDetails.loadUserByUsername(owner.getEmail()))))
                .andExpect(status().isNotFound());
        assertThrows(WorkspaceForbiddenException.class,
                () -> sharedTasks.create(viewer.getId(), workspaceId, "Forbidden", "", TaskPriority.MEDIUM, null));
        assertThrows(WorkspaceForbiddenException.class,
                () -> sharedTasks.assign(owner.getId(), workspaceId, taskId, viewer.getId()));
        assertThrows(TaskNotFoundException.class,
                () -> sharedTasks.assign(owner.getId(), workspaceId, taskId, otherOwner.getId()));
        assertThrows(TaskRuleException.class, () -> workspaces.removeMember(owner.getId(), workspaceId, owner.getId()));

        sharedTasks.assign(owner.getId(), workspaceId, taskId, member.getId());
        UUID commentId = sharedTasks.comment(owner.getId(), workspaceId, taskId,
                "<script>alert(1)</script>", List.of(member.getId()));
        assertEquals("<script>alert(1)</script>", sharedTasks.comments(owner.getId(), workspaceId, taskId).getFirst().body());
        assertTrue(sharedTasks.notifications(member.getId()).stream().anyMatch(item -> item.getType() == WorkspaceNotificationType.ASSIGNMENT));
        assertTrue(sharedTasks.notifications(member.getId()).stream().anyMatch(item -> item.getType() == WorkspaceNotificationType.MENTION));
        assertTrue(sharedTasks.notifications(viewer.getId()).stream().anyMatch(item -> item.getType() == WorkspaceNotificationType.ACTIVITY));

        mvc.perform(get("/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId)
                        .with(user(userDetails.loadUserByUsername(owner.getEmail()))))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
                    assertFalse(html.contains("<script>alert(1)</script>"));
                });
        sharedTasks.editComment(owner.getId(), workspaceId, taskId, commentId, "Updated mention", List.of(viewer.getId()));
        assertTrue(sharedTasks.notifications(viewer.getId()).stream().anyMatch(item -> item.getType() == WorkspaceNotificationType.MENTION));

        workspaces.removeMember(owner.getId(), workspaceId, member.getId());
        assertEquals(MembershipStatus.REMOVED, memberships.findByWorkspace_IdAndUser_Id(workspaceId, member.getId()).orElseThrow().getStatus());
        assertTrue(sharedTasks.notifications(member.getId()).isEmpty(), "removed members must lose access to workspace notifications");
        assertThrows(TaskNotFoundException.class, () -> sharedTasks.task(member.getId(), workspaceId, taskId));
        assertNull(sharedTasks.assignee(owner.getId(), workspaceId, taskId), "member removal clears assignments");
        workspaces.archive(owner.getId(), workspaceId);
        assertThrows(WorkspaceForbiddenException.class,
                () -> sharedTasks.editComment(owner.getId(), workspaceId, taskId, commentId, "Archived edit", List.of()));
        assertThrows(WorkspaceForbiddenException.class,
                () -> sharedTasks.deleteComment(owner.getId(), workspaceId, taskId, commentId));
    }

    @Test
    void workspaceDashboardRendersForItsActiveOwner() throws Exception {
        UserAccount owner = register("Workspace page owner");
        UUID workspaceId = workspaces.create(owner.getId(), "Render workspace", "Private by default");
        var principal = userDetails.loadUserByUsername(owner.getEmail());

        mvc.perform(get("/teams").with(user(principal))).andExpect(status().isOk());
        String html = mvc.perform(get("/workspaces/{id}", workspaceId).with(user(principal)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Switch workspace"));
        assertTrue(html.contains("Render workspace"));
        mvc.perform(get("/workspaces/{id}/members", workspaceId).with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/workspaces/{id}/workload", workspaceId).with(user(principal))).andExpect(status().isOk());
    }

    @Test
    void sharedTaskStatusUpdatesKeepItsExistingKanbanPlacementSynchronized() throws Exception {
        UserAccount owner = register("Project owner");
        UUID workspaceId = workspaces.create(owner.getId(), "Kanban team", "");
        ProjectForm form = new ProjectForm();
        form.setName("Shared project");
        form.setStatus(ProjectStatus.ACTIVE);
        UUID projectId = projects.create(owner.getId(), form, null);
        UUID todoColumn = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(owner.getId(), projectId).stream()
                .filter(column -> column.getStage() == BoardStage.TODO).findFirst().orElseThrow().getId();
        UUID taskId = projects.createTask(owner.getId(), projectId, todoColumn,
                "Kanban status sync", "", TaskPriority.MEDIUM, null);
        String review = mvc.perform(get("/projects/{id}/share", projectId).param("workspaceId", workspaceId.toString())
                        .with(user(userDetails.loadUserByUsername(owner.getEmail()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(review.contains("Task details becoming visible"));
        assertTrue(review.contains("Kanban status sync"));
        sharedProjects.share(owner.getId(), workspaceId, projectId, true);

        sharedTasks.changeStatus(owner.getId(), workspaceId, taskId, TaskStatus.IN_PROGRESS);

        assertEquals(TaskStatus.IN_PROGRESS, sharedTasks.task(owner.getId(), workspaceId, taskId).getStatus());
        assertEquals(BoardStage.IN_PROGRESS,
                placements.findByTaskIdAndOwnerId(taskId, owner.getId()).orElseThrow().getColumn().getStage());
    }

    @Test
    void sharedProjectsExposeScrumAndGanttToMembersButRejectViewerWrites() throws Exception {
        UserAccount owner = register("Project owner");
        UserAccount member = register("Project member");
        UserAccount viewer = register("Project viewer");
        UUID workspaceId = workspaces.create(owner.getId(), "Shared tools", "");
        acceptInvite(owner, workspaceId, member, WorkspaceRole.MEMBER);
        acceptInvite(owner, workspaceId, viewer, WorkspaceRole.VIEWER);
        ProjectForm form = new ProjectForm();
        form.setName("Shared delivery"); form.setStatus(ProjectStatus.ACTIVE);
        UUID projectId = projects.create(owner.getId(), form, null);
        UUID todo = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(owner.getId(), projectId).stream()
                .filter(column -> column.getStage() == BoardStage.TODO).findFirst().orElseThrow().getId();
        UUID first = projects.createTask(owner.getId(), projectId, todo, "First shared task", "", TaskPriority.MEDIUM, null);
        UUID second = projects.createTask(owner.getId(), projectId, todo, "Second shared task", "", TaskPriority.MEDIUM, null);
        agile.configure(owner.getId(), projectId, true, "Ready for review");
        sharedProjects.share(owner.getId(), workspaceId, projectId, true);
        var memberPrincipal = userDetails.loadUserByUsername(member.getEmail());
        var viewerPrincipal = userDetails.loadUserByUsername(viewer.getEmail());
        assertEquals(projectId, sharedProjects.sharedProject(member.getId(), workspaceId, projectId).getId());
        assertEquals(7, projects.sharedBoard(member.getId(), projectId).size());

        mvc.perform(get("/projects/{id}/agile", projectId).with(user(memberPrincipal))).andExpect(status().isOk());
        mvc.perform(get("/projects/{id}/backlog", projectId).with(user(memberPrincipal))).andExpect(status().isOk());
        mvc.perform(get("/projects/{id}/gantt", projectId).with(user(memberPrincipal))).andExpect(status().isOk());
        mvc.perform(get("/projects/{id}/dependencies", projectId).with(user(viewerPrincipal))).andExpect(status().isOk());
        mvc.perform(get("/workspaces/{workspaceId}/projects/{projectId}", workspaceId, projectId)
                        .with(user(memberPrincipal))).andExpect(status().isOk());
        mvc.perform(post("/projects/{id}/backlog/create", projectId).with(user(memberPrincipal)).with(csrf())
                        .param("title", "Member-created Scrum task").param("description", "Created through shared Scrum")
                        .param("priority", "MEDIUM"))
                .andExpect(status().is3xxRedirection());
        UUID memberTaskId = placements.findBoard(owner.getId(), projectId).stream()
                .filter(item -> item.getTask().getTitle().equals("Member-created Scrum task"))
                .map(item -> item.getTaskId()).findFirst().orElseThrow();
        assertEquals(owner.getId(), tasks.findByIdAndWorkspace(memberTaskId, workspaceId).orElseThrow().getOwner().getId());
        assertTrue(workspaces.activities(member.getId(), workspaceId).stream()
                .anyMatch(event -> event.getActorId().equals(member.getId()) && event.getSummary().contains("Member-created Scrum task")));
        sharedTasks.changeStatus(member.getId(), workspaceId, memberTaskId, TaskStatus.IN_PROGRESS);
        assertEquals(BoardStage.IN_PROGRESS,
                placements.findByTaskIdAndOwnerId(memberTaskId, owner.getId()).orElseThrow().getColumn().getStage());

        UUID inProgress = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(owner.getId(), projectId).stream()
                .filter(column -> column.getStage() == BoardStage.IN_PROGRESS).findFirst().orElseThrow().getId();
        mvc.perform(post("/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/move", workspaceId, projectId, second)
                        .with(user(memberPrincipal)).with(csrf()).param("columnId", inProgress.toString()))
                .andExpect(status().is3xxRedirection());
        assertEquals(BoardStage.IN_PROGRESS,
                placements.findByTaskIdAndOwnerId(second, owner.getId()).orElseThrow().getColumn().getStage());
        mvc.perform(post("/workspaces/{workspaceId}/projects/{projectId}/tasks/{taskId}/move", workspaceId, projectId, second)
                        .with(user(viewerPrincipal)).with(csrf()).param("columnId", todo.toString()))
                .andExpect(status().isForbidden());

        LocalDate sprintStart = LocalDate.now(ZoneOffset.UTC);
        LocalDate sprintEnd = sprintStart.plusDays(13);
        mvc.perform(post("/projects/{id}/sprints", projectId).with(user(memberPrincipal)).with(csrf())
                        .param("name", "Team sprint").param("goal", "Ship shared work")
                        .param("startDate", sprintStart.toString()).param("endDate", sprintEnd.toString()))
                .andExpect(status().is3xxRedirection());
        assertEquals(1, agile.sprints(owner.getId(), projectId).size());
        mvc.perform(post("/projects/{id}/sprints", projectId).with(user(viewerPrincipal)).with(csrf())
                        .param("name", "Forbidden sprint").param("goal", "No write")
                        .param("startDate", "2026-10-26").param("endDate", "2026-11-08"))
                .andExpect(status().isForbidden());
        UUID sprintId = agile.sprints(owner.getId(), projectId).getFirst().getId();
        agile.addToSprint(owner.getId(), projectId, sprintId, memberTaskId, null);
        agile.startSprint(owner.getId(), projectId, sprintId);
        mvc.perform(post("/projects/{projectId}/sprints/{sprintId}/board/{taskId}/move", projectId, sprintId, memberTaskId)
                        .with(user(memberPrincipal)).with(csrf()).param("columnId", todo.toString()))
                .andExpect(status().is3xxRedirection());
        assertEquals(TaskStatus.TODO, tasks.findByIdAndWorkspace(memberTaskId, workspaceId).orElseThrow().getStatus());
        assertEquals(BoardStage.TODO,
                placements.findByTaskIdAndOwnerId(memberTaskId, owner.getId()).orElseThrow().getColumn().getStage());
        mvc.perform(post("/projects/{id}/scheduling/dependencies", projectId).with(user(memberPrincipal)).with(csrf())
                        .param("predecessorTaskId", first.toString()).param("successorTaskId", second.toString())
                        .param("type", TaskDependencyType.FS.name()).param("lagDays", "0"))
                .andExpect(status().is3xxRedirection());
        assertEquals(1, dependencies.findAllByOwnerIdAndProjectIdOrderByCreatedAtAsc(owner.getId(), projectId).size());
        mvc.perform(post("/projects/{id}/scheduling/dependencies", projectId).with(user(viewerPrincipal)).with(csrf())
                        .param("predecessorTaskId", second.toString()).param("successorTaskId", first.toString())
                        .param("type", TaskDependencyType.FS.name()).param("lagDays", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    void workspaceCalendarShowsOnlySharedTasksAndAllowsViewerReads() throws Exception {
        UserAccount owner = register("Calendar owner");
        UserAccount otherOwner = register("Other calendar owner");
        UserAccount viewer = register("Calendar viewer");
        UUID workspaceId = workspaces.create(owner.getId(), "Calendar team", "");
        UUID otherWorkspaceId = workspaces.create(otherOwner.getId(), "Other calendar", "");
        acceptInvite(owner, workspaceId, viewer, WorkspaceRole.VIEWER);
        sharedTasks.create(owner.getId(), workspaceId, "Team due date", "", TaskPriority.HIGH,
                java.time.LocalDate.of(2026, 10, 26));
        sharedTasks.create(otherOwner.getId(), otherWorkspaceId, "Private other due date", "", TaskPriority.MEDIUM,
                java.time.LocalDate.of(2026, 10, 26));

        String html = mvc.perform(get("/workspaces/{id}/calendar", workspaceId).param("month", "2026-10-01")
                        .with(user(userDetails.loadUserByUsername(viewer.getEmail()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Team due date"));
        assertFalse(html.contains("Private other due date"));
        assertTrue(html.contains("October 2026"));
    }

    @Test
    void revocationTransferAndArchiveRulesMatchWorkspaceRoles() {
        UserAccount owner = register("Original owner");
        UserAccount nextOwner = register("Next owner");
        UUID workspaceId = workspaces.create(owner.getId(), "Lifecycle workspace", "");
        String token = invitations.create(owner.getId(), workspaceId, nextOwner.getEmail(), WorkspaceRole.MEMBER);
        UUID invitationId = invitations.list(owner.getId(), workspaceId).getFirst().getId();
        invitations.revoke(owner.getId(), workspaceId, invitationId);
        assertThrows(TaskNotFoundException.class, () -> invitations.inspect(token, nextOwner.getEmail()));

        acceptInvite(owner, workspaceId, nextOwner, WorkspaceRole.MEMBER);
        workspaces.transferOwnership(owner.getId(), workspaceId, nextOwner.getId());
        assertEquals(WorkspaceRole.ADMIN, memberships.findByWorkspace_IdAndUser_Id(workspaceId, owner.getId()).orElseThrow().getRole());
        assertEquals(WorkspaceRole.OWNER, memberships.findByWorkspace_IdAndUser_Id(workspaceId, nextOwner.getId()).orElseThrow().getRole());
        assertThrows(WorkspaceForbiddenException.class, () -> workspaces.archive(owner.getId(), workspaceId));

        workspaces.archive(nextOwner.getId(), workspaceId);
        assertThrows(WorkspaceForbiddenException.class,
                () -> workspaces.setCapacity(nextOwner.getId(), workspaceId, nextOwner.getId(), 4));
        workspaces.restore(nextOwner.getId(), workspaceId);
    }

    @Test
    void notificationPreferencesControlAssignmentMentionAndActivityDelivery() throws Exception {
        UserAccount owner = register("Preference owner");
        UserAccount member = register("Quiet member");
        UUID workspaceId = workspaces.create(owner.getId(), "Notification preferences", "");
        acceptInvite(owner, workspaceId, member, WorkspaceRole.MEMBER);
        workspaces.setNotificationPreferences(member.getId(), workspaceId, false, false, false);
        UUID taskId = sharedTasks.create(owner.getId(), workspaceId, "Preference test", "", TaskPriority.MEDIUM, null);

        sharedTasks.assign(owner.getId(), workspaceId, taskId, member.getId());
        sharedTasks.comment(owner.getId(), workspaceId, taskId, "A direct mention", List.of(member.getId()));

        assertTrue(sharedTasks.notifications(member.getId()).isEmpty());
        var membership = memberships.findByWorkspace_IdAndUser_Id(workspaceId, member.getId()).orElseThrow();
        assertFalse(membership.isNotifyAssignments());
        assertFalse(membership.isNotifyMentions());
        assertFalse(membership.isNotifyActivity());

        mvc.perform(post("/workspaces/{id}/notifications/preferences", workspaceId)
                        .with(user(userDetails.loadUserByUsername(member.getEmail()))).with(csrf())
                        .param("notifyActivity", "true"))
                .andExpect(status().is3xxRedirection());
        assertTrue(memberships.findByWorkspace_IdAndUser_Id(workspaceId, member.getId()).orElseThrow().isNotifyActivity());
    }

    private UserAccount register(String label) {
        String email = label.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID() + "@example.test";
        return registration.register(label, email, PASSWORD);
    }

    private void acceptInvite(UserAccount owner, UUID workspaceId, UserAccount invitee, WorkspaceRole role) {
        String token = invitations.create(owner.getId(), workspaceId, invitee.getEmail(), role);
        invitations.accept(token, invitee.getId(), invitee.getEmail());
    }
}
