package com.taskos.collaboration.service;

import com.taskos.collaboration.dto.WorkspaceSummary;
import com.taskos.collaboration.dto.WorkspaceSwitcherOption;
import com.taskos.collaboration.entity.MembershipStatus;
import com.taskos.collaboration.entity.Workspace;
import com.taskos.collaboration.entity.WorkspaceActivity;
import com.taskos.collaboration.entity.WorkspaceEventType;
import com.taskos.collaboration.entity.WorkspaceMembership;
import com.taskos.collaboration.entity.WorkspaceNotification;
import com.taskos.collaboration.entity.WorkspaceNotificationType;
import com.taskos.collaboration.entity.WorkspaceRole;
import com.taskos.collaboration.entity.WorkspaceStatus;
import com.taskos.collaboration.entity.AssignmentAction;
import com.taskos.collaboration.entity.TaskAssignment;
import com.taskos.collaboration.entity.TaskAssignmentHistory;
import com.taskos.collaboration.repository.WorkspaceActivityRepository;
import com.taskos.collaboration.repository.WorkspaceMembershipRepository;
import com.taskos.collaboration.repository.WorkspaceNotificationRepository;
import com.taskos.collaboration.repository.WorkspaceRepository;
import com.taskos.collaboration.repository.TaskAssignmentHistoryRepository;
import com.taskos.collaboration.repository.TaskAssignmentRepository;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkspaceService {
    private static final List<TaskStatus> OPEN_STATUSES = List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.ON_HOLD);
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceActivityRepository activity;
    private final WorkspaceAuthorizationService authorization;
    private final UserAccountRepository users;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final TaskAssignmentHistoryRepository assignmentHistory;
    private final WorkspaceNotificationRepository notifications;

    public WorkspaceService(WorkspaceRepository workspaces, WorkspaceMembershipRepository memberships,
            WorkspaceActivityRepository activity, WorkspaceAuthorizationService authorization,
            UserAccountRepository users, ProjectRepository projects, TaskRepository tasks,
            TaskAssignmentRepository assignments, TaskAssignmentHistoryRepository assignmentHistory,
            WorkspaceNotificationRepository notifications) {
        this.workspaces = workspaces; this.memberships = memberships; this.activity = activity;
        this.authorization = authorization; this.users = users; this.projects = projects; this.tasks = tasks;
        this.assignments = assignments; this.assignmentHistory = assignmentHistory; this.notifications = notifications;
    }

    public UUID create(UUID creatorId, String name, String description) {
        validate(name, description);
        UserAccount creator = users.findById(creatorId).orElseThrow(TaskNotFoundException::new);
        Instant now = Instant.now();
        Workspace workspace = workspaces.saveAndFlush(Workspace.create(creator, name, description, now));
        memberships.save(WorkspaceMembership.create(workspace, creator, WorkspaceRole.OWNER, now));
        record(workspace.getId(), creatorId, WorkspaceEventType.WORKSPACE_CREATED, "Workspace created", now);
        return workspace.getId();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceSummary> list(UUID userId) {
        return memberships.findAllByUser_IdAndStatusOrderByJoinedAtAsc(userId, MembershipStatus.ACTIVE).stream()
                .map(membership -> {
                    UUID id = membership.getWorkspace().getId();
                    return new WorkspaceSummary(id, membership.getWorkspace().getName(), membership.getWorkspace().getDescription(),
                            membership.getRole(), memberships.countByWorkspace_IdAndStatus(id, MembershipStatus.ACTIVE),
                            projects.countByWorkspace_IdAndArchivedAtIsNull(id),
                            tasks.countByWorkspace_IdAndStatusInAndArchivedAtIsNull(id, OPEN_STATUSES));
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceSwitcherOption> switcherOptions(UUID userId) {
        return memberships.findAllByUser_IdAndStatusOrderByJoinedAtAsc(userId, MembershipStatus.ACTIVE).stream()
                .map(membership -> new WorkspaceSwitcherOption(membership.getWorkspace().getId(),
                        membership.getWorkspace().getName(), membership.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Workspace workspace(UUID actorId, UUID workspaceId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        return workspaces.findById(workspaceId).orElseThrow(TaskNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMembership> members(UUID actorId, UUID workspaceId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        return memberships.findAllByWorkspace_IdAndStatusOrderByJoinedAtAsc(workspaceId, MembershipStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceActivity> activities(UUID actorId, UUID workspaceId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        return activity.findAllByWorkspaceIdOrderByOccurredAtDesc(workspaceId, PageRequest.of(0, 30));
    }

    public void update(UUID actorId, UUID workspaceId, String name, String description) {
        validate(name, description);
        Workspace workspace = lockedWorkspace(actorId, workspaceId, WorkspacePermission.EDIT_SETTINGS);
        Instant now = Instant.now(); workspace.update(name, description, now);
        record(workspaceId, actorId, WorkspaceEventType.WORKSPACE_UPDATED, "Workspace settings updated", now);
    }

    public void archive(UUID actorId, UUID workspaceId) {
        Workspace workspace = workspaces.findLockedById(workspaceId).orElseThrow(TaskNotFoundException::new);
        if (authorization.requireLocked(actorId, workspaceId, WorkspacePermission.EDIT_SETTINGS).getRole() != WorkspaceRole.OWNER)
            throw new WorkspaceForbiddenException();
        Instant now = Instant.now(); workspace.archive(now);
        record(workspaceId, actorId, WorkspaceEventType.WORKSPACE_ARCHIVED, "Workspace archived", now);
    }

    public void restore(UUID actorId, UUID workspaceId) {
        Workspace workspace = workspaces.findLockedById(workspaceId).orElseThrow(TaskNotFoundException::new);
        WorkspaceMembership membership = memberships.findByWorkspace_IdAndUser_Id(workspaceId, actorId)
                .filter(row -> row.getStatus() == MembershipStatus.ACTIVE).orElseThrow(TaskNotFoundException::new);
        if (membership.getRole() != WorkspaceRole.OWNER) throw new WorkspaceForbiddenException();
        Instant now = Instant.now(); workspace.restore(now);
        record(workspaceId, actorId, WorkspaceEventType.WORKSPACE_RESTORED, "Workspace restored", now);
    }

    public void changeRole(UUID actorId, UUID workspaceId, UUID memberId, WorkspaceRole nextRole) {
        if (nextRole == null || nextRole == WorkspaceRole.OWNER) throw new TaskRuleException("Use ownership transfer to change the workspace owner.");
        lockedWorkspace(actorId, workspaceId, WorkspacePermission.MANAGE_MEMBERS);
        WorkspaceMembership actor = memberships.findActive(workspaceId, actorId, MembershipStatus.ACTIVE).orElseThrow(TaskNotFoundException::new);
        WorkspaceMembership target = memberships.findLocked(workspaceId, memberId).filter(row -> row.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(TaskNotFoundException::new);
        if (actor.getRole() == WorkspaceRole.ADMIN && (target.getRole() != WorkspaceRole.MEMBER && target.getRole() != WorkspaceRole.VIEWER
                || nextRole == WorkspaceRole.ADMIN)) throw new WorkspaceForbiddenException();
        if (target.getRole() == WorkspaceRole.OWNER) throw new TaskRuleException("Transfer ownership before changing the owner's role.");
        if (target.getRole() == nextRole) return;
        target.changeRole(nextRole);
        record(workspaceId, actorId, WorkspaceEventType.ROLE_CHANGED, target.getUser().getDisplayName() + " is now " + nextRole.name().toLowerCase(), Instant.now());
    }

    public void removeMember(UUID actorId, UUID workspaceId, UUID memberId) {
        lockedWorkspace(actorId, workspaceId, WorkspacePermission.MANAGE_MEMBERS);
        WorkspaceMembership actor = memberships.findActive(workspaceId, actorId, MembershipStatus.ACTIVE).orElseThrow(TaskNotFoundException::new);
        WorkspaceMembership target = memberships.findLocked(workspaceId, memberId).filter(row -> row.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(TaskNotFoundException::new);
        if (actor.getRole() == WorkspaceRole.ADMIN && target.getRole() != WorkspaceRole.MEMBER && target.getRole() != WorkspaceRole.VIEWER)
            throw new WorkspaceForbiddenException();
        if (target.getRole() == WorkspaceRole.OWNER
                && memberships.countByWorkspace_IdAndRoleAndStatus(workspaceId, WorkspaceRole.OWNER, MembershipStatus.ACTIVE) == 1)
            throw new TaskRuleException("A workspace must keep at least one owner. Transfer ownership first.");
        Instant now = Instant.now();
        for (TaskAssignment assignment : assignments.findAllByWorkspaceIdAndAssigneeId(workspaceId, memberId)) {
            assignmentHistory.save(TaskAssignmentHistory.create(workspaceId, assignment.getTaskId(), memberId, actorId,
                    AssignmentAction.UNASSIGNED, now));
            assignments.delete(assignment);
        }
        target.remove();
        record(workspaceId, actorId, WorkspaceEventType.MEMBER_REMOVED, target.getUser().getDisplayName() + " removed from the workspace", now);
    }

    public void transferOwnership(UUID actorId, UUID workspaceId, UUID nextOwnerId) {
        lockedWorkspace(actorId, workspaceId, WorkspacePermission.EDIT_SETTINGS);
        WorkspaceMembership current = memberships.findLocked(workspaceId, actorId).filter(row -> row.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(TaskNotFoundException::new);
        WorkspaceMembership nextOwner = memberships.findLocked(workspaceId, nextOwnerId).filter(row -> row.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(TaskNotFoundException::new);
        if (current.getRole() != WorkspaceRole.OWNER) throw new WorkspaceForbiddenException();
        if (current.getUser().getId().equals(nextOwnerId)) throw new TaskRuleException("Choose another active member as the new owner.");
        current.changeRole(WorkspaceRole.ADMIN); nextOwner.changeRole(WorkspaceRole.OWNER);
        record(workspaceId, actorId, WorkspaceEventType.OWNERSHIP_TRANSFERRED,
                "Ownership transferred to " + nextOwner.getUser().getDisplayName(), Instant.now());
    }

    public void setCapacity(UUID actorId, UUID workspaceId, UUID memberId, Integer maxOpenTasks) {
        if (maxOpenTasks != null && (maxOpenTasks < 1 || maxOpenTasks > 500)) throw new TaskRuleException("Choose a task limit between 1 and 500, or clear it.");
        WorkspaceMembership actor = authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        if (actor.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE) throw new WorkspaceForbiddenException();
        WorkspaceMembership target = memberships.findLocked(workspaceId, memberId).filter(row -> row.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(TaskNotFoundException::new);
        if (!actorId.equals(memberId) && actor.getRole() != WorkspaceRole.OWNER && actor.getRole() != WorkspaceRole.ADMIN)
            throw new WorkspaceForbiddenException();
        target.setMaxOpenTasks(maxOpenTasks);
    }

    public void setNotificationPreferences(UUID actorId, UUID workspaceId, boolean assignments, boolean mentions, boolean activity) {
        WorkspaceMembership membership = authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        if (membership.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE) throw new WorkspaceForbiddenException();
        membership.setNotificationPreferences(assignments, mentions, activity);
    }

    public void record(UUID workspaceId, UUID actorId, WorkspaceEventType type, String summary, Instant now) {
        String activitySummary = summary.length() <= 240 ? summary : summary.substring(0, 239) + "…";
        activity.save(WorkspaceActivity.create(workspaceId, actorId, type, activitySummary, now));
        String title = switch (type) {
            case INVITATION_CREATED -> "A workspace invitation was created";
            case INVITATION_REVOKED -> "A workspace invitation was revoked";
            default -> activitySummary;
        };
        if (title.length() > 160) title = title.substring(0, 160);
        String notificationTitle = title;
        memberships.findAllByWorkspace_IdAndStatusOrderByJoinedAtAsc(workspaceId, MembershipStatus.ACTIVE).stream()
                .filter(member -> !member.getUser().getId().equals(actorId) && member.isNotifyActivity())
                .forEach(member -> notifications.save(WorkspaceNotification.deliver(workspaceId, member.getUser().getId(), null,
                        WorkspaceNotificationType.ACTIVITY, notificationTitle, now)));
    }

    private Workspace lockedWorkspace(UUID actorId, UUID workspaceId, WorkspacePermission permission) {
        Workspace workspace = workspaces.findLockedById(workspaceId).orElseThrow(TaskNotFoundException::new);
        authorization.requireLocked(actorId, workspaceId, permission);
        return workspace;
    }

    private static void validate(String name, String description) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) throw new TaskRuleException("Workspace name must be between 1 and 100 characters.");
        if (description != null && description.length() > 1000) throw new TaskRuleException("Workspace description must be 1000 characters or fewer.");
    }
}
