package com.taskos.collaboration.service;

import com.taskos.collaboration.dto.AssignedTask;
import com.taskos.collaboration.dto.SharedComment;
import com.taskos.collaboration.dto.WorkloadMember;
import com.taskos.collaboration.dto.WorkspaceCalendarDay;
import com.taskos.collaboration.dto.WorkspaceCalendarPage;
import com.taskos.collaboration.entity.AssignmentAction;
import com.taskos.collaboration.entity.MembershipStatus;
import com.taskos.collaboration.entity.TaskAssignment;
import com.taskos.collaboration.entity.TaskAssignmentHistory;
import com.taskos.collaboration.entity.TaskComment;
import com.taskos.collaboration.entity.TaskCommentMention;
import com.taskos.collaboration.entity.Workspace;
import com.taskos.collaboration.entity.WorkspaceActivity;
import com.taskos.collaboration.entity.WorkspaceEventType;
import com.taskos.collaboration.entity.WorkspaceMembership;
import com.taskos.collaboration.entity.WorkspaceNotification;
import com.taskos.collaboration.entity.WorkspaceNotificationType;
import com.taskos.collaboration.entity.WorkspaceRole;
import com.taskos.collaboration.repository.TaskAssignmentHistoryRepository;
import com.taskos.collaboration.repository.TaskAssignmentRepository;
import com.taskos.collaboration.repository.TaskCommentMentionRepository;
import com.taskos.collaboration.repository.TaskCommentRepository;
import com.taskos.collaboration.repository.WorkspaceMembershipRepository;
import com.taskos.collaboration.repository.WorkspaceNotificationRepository;
import com.taskos.collaboration.repository.WorkspaceRepository;
import com.taskos.project.entity.ProjectTaskPlacement;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.project.repository.ProjectTaskPlacementRepository;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.event.TaskStatusChangedEvent;
import com.taskos.automation.entity.AutomationTriggerType;
import com.taskos.automation.service.AutomationOutboxService;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkspaceTaskService {
    private static final List<TaskStatus> OPEN = List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.ON_HOLD);
    private static final List<TaskPriority> HIGH = List.of(TaskPriority.HIGH, TaskPriority.URGENT);
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final TaskAssignmentHistoryRepository assignmentHistory;
    private final TaskCommentRepository comments;
    private final TaskCommentMentionRepository mentions;
    private final WorkspaceNotificationRepository notifications;
    private final UserAccountRepository users;
    private final WorkspaceService workspaceService;
    private final ProjectTaskPlacementRepository placements;
    private final ProjectRepository projects;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AutomationOutboxService automationOutbox;

    public WorkspaceTaskService(WorkspaceAuthorizationService authorization, WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships, TaskRepository tasks, TaskAssignmentRepository assignments,
            TaskAssignmentHistoryRepository assignmentHistory, TaskCommentRepository comments,
            TaskCommentMentionRepository mentions, WorkspaceNotificationRepository notifications,
            UserAccountRepository users, WorkspaceService workspaceService, ProjectTaskPlacementRepository placements,
            ProjectRepository projects, ApplicationEventPublisher events, Clock clock, AutomationOutboxService automationOutbox) {
        this.authorization = authorization; this.workspaces = workspaces; this.memberships = memberships; this.tasks = tasks;
        this.assignments = assignments; this.assignmentHistory = assignmentHistory; this.comments = comments;
        this.mentions = mentions; this.notifications = notifications; this.users = users; this.workspaceService = workspaceService;
        this.placements = placements; this.projects = projects; this.events = events; this.clock = clock;
        this.automationOutbox = automationOutbox;
    }

    public UUID create(UUID actorId, UUID workspaceId, String title, String description, TaskPriority priority, LocalDate dueDate) {
        return create(actorId, actorId, workspaceId, title, description, priority, dueDate);
    }

    public UUID createForProject(UUID actorId, UUID projectOwnerId, UUID workspaceId,
            String title, String description, TaskPriority priority, LocalDate dueDate) {
        return create(actorId, projectOwnerId, workspaceId, title, description, priority, dueDate, null, null, 0);
    }

    public UUID createFromAutomation(UUID actorId, UUID workspaceId, String title, String description,
            TaskPriority priority, LocalDate dueDate, UUID executionId, UUID causationId, int chainDepth) {
        return create(actorId, actorId, workspaceId, title, description, priority, dueDate, executionId, causationId, chainDepth);
    }

    private UUID create(UUID actorId, UUID taskOwnerId, UUID workspaceId,
            String title, String description, TaskPriority priority, LocalDate dueDate) {
        return create(actorId, taskOwnerId, workspaceId, title, description, priority, dueDate, null, null, 0);
    }

    private UUID create(UUID actorId, UUID taskOwnerId, UUID workspaceId, String title, String description,
            TaskPriority priority, LocalDate dueDate, UUID executionId, UUID causationId, int chainDepth) {
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.EDIT_TASK);
        if (title == null || title.trim().isEmpty() || title.trim().length() > 160) throw new TaskRuleException("Task title must be between 1 and 160 characters.");
        if (description != null && description.length() > 6000) throw new TaskRuleException("Task description must be 6000 characters or fewer.");
        Workspace workspace = workspaces.findById(workspaceId).orElseThrow(TaskNotFoundException::new);
        UserAccount taskOwner = users.findById(taskOwnerId).orElseThrow(TaskNotFoundException::new);
        Instant now = clock.instant();
        Task task = tasks.save(Task.createInWorkspace(taskOwner, workspace, title.trim(), description, TaskStatus.TODO,
                priority == null ? TaskPriority.MEDIUM : priority, null, dueDate, now));
        workspaceService.record(workspaceId, actorId, WorkspaceEventType.TASK_CREATED,
                "Shared task created: “" + task.getTitle() + "”", now);
        automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_CREATED, actorId, workspaceId, task.getId(),
                automationData(task, null), causationId, executionId, chainDepth);
        return task.getId();
    }

    @Transactional(readOnly = true)
    public List<Task> tasks(UUID actorId, UUID workspaceId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        return tasks.findAllByWorkspace_IdAndArchivedAtIsNullOrderByUpdatedAtDesc(workspaceId, PageRequest.of(0, 100));
    }

    @Transactional(readOnly = true)
    public Task task(UUID actorId, UUID workspaceId, UUID taskId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        return tasks.findByIdAndWorkspace(taskId, workspaceId).orElseThrow(TaskNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public WorkspaceMembership assignee(UUID actorId, UUID workspaceId, UUID taskId) {
        task(actorId, workspaceId, taskId);
        return assignments.findByTaskIdAndWorkspaceId(taskId, workspaceId)
                .flatMap(row -> memberships.findByWorkspace_IdAndUser_Id(workspaceId, row.getAssigneeId()))
                .filter(row -> row.getStatus() == MembershipStatus.ACTIVE).orElse(null);
    }

    public void assign(UUID actorId, UUID workspaceId, UUID taskId, UUID assigneeId) {
        assign(actorId, workspaceId, taskId, assigneeId, null, null, 0);
    }

    public void assignFromAutomation(UUID actorId, UUID workspaceId, UUID taskId, UUID assigneeId,
            UUID executionId, UUID causationId, int chainDepth) {
        assign(actorId, workspaceId, taskId, assigneeId, executionId, causationId, chainDepth);
    }

    private void assign(UUID actorId, UUID workspaceId, UUID taskId, UUID assigneeId,
            UUID executionId, UUID causationId, int chainDepth) {
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.ASSIGN_TASK);
        Task task = tasks.findLockedByIdAndWorkspace(taskId, workspaceId).orElseThrow(TaskNotFoundException::new);
        TaskAssignment current = assignments.findByTaskIdAndWorkspaceId(taskId, workspaceId).orElse(null);
        WorkspaceMembership assigneeMembership = null;
        if (assigneeId != null) {
            WorkspaceMembership target = memberships.findLocked(workspaceId, assigneeId)
                    .filter(row -> row.getStatus() == MembershipStatus.ACTIVE).orElseThrow(TaskNotFoundException::new);
            if (target.getRole() == WorkspaceRole.VIEWER) throw new WorkspaceForbiddenException();
            if (current != null && current.getAssigneeId().equals(assigneeId)) return;
            assigneeMembership = target;
        } else if (current == null) return;
        Instant now = clock.instant();
        AssignmentAction action;
        if (assigneeId == null) {
            assignments.delete(current); action = AssignmentAction.UNASSIGNED;
        } else if (current == null) {
            assignments.save(TaskAssignment.create(workspaceId, taskId, assigneeId, actorId, now)); action = AssignmentAction.ASSIGNED;
        } else {
            current.reassign(assigneeId, actorId, now); action = AssignmentAction.REASSIGNED;
        }
        UUID historyAssignee = assigneeId == null ? current.getAssigneeId() : assigneeId;
        assignmentHistory.save(TaskAssignmentHistory.create(workspaceId, taskId, historyAssignee, actorId, action, now));
        workspaceService.record(workspaceId, actorId,
                assigneeId == null ? WorkspaceEventType.TASK_UNASSIGNED : WorkspaceEventType.TASK_ASSIGNED,
                assigneeId == null ? "Assignment removed from “" + task.getTitle() + "”"
                        : "“" + task.getTitle() + "” assigned to " + users.findById(assigneeId).orElseThrow(TaskNotFoundException::new).getDisplayName(), now);
        if (assigneeId != null && !assigneeId.equals(actorId) && assigneeMembership.isNotifyAssignments())
            notifications.save(WorkspaceNotification.deliver(workspaceId, assigneeId, taskId,
                    WorkspaceNotificationType.ASSIGNMENT, limitNotificationTitle("You were assigned “" + task.getTitle() + "”"), now));
        automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_ASSIGNED, actorId, workspaceId, taskId,
                automationData(task, null, assigneeId), causationId, executionId, chainDepth);
    }

    public void changeStatus(UUID actorId, UUID workspaceId, UUID taskId, TaskStatus status) {
        changeStatus(actorId, workspaceId, taskId, status, null, null, 0);
    }

    public void changeStatusFromAutomation(UUID actorId, UUID workspaceId, UUID taskId, TaskStatus status,
            UUID executionId, UUID causationId, int chainDepth) {
        changeStatus(actorId, workspaceId, taskId, status, executionId, causationId, chainDepth);
    }

    private void changeStatus(UUID actorId, UUID workspaceId, UUID taskId, TaskStatus status,
            UUID executionId, UUID causationId, int chainDepth) {
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.EDIT_TASK);
        Task task = tasks.findLockedByIdAndWorkspace(taskId, workspaceId).orElseThrow(TaskNotFoundException::new);
        if (status == null) throw new TaskRuleException("Choose a task status.");
        TaskStatus before = task.getStatus();
        Instant now = clock.instant();
        try { if (!task.changeStatus(status, now)) return; }
        catch (IllegalArgumentException ex) { throw new TaskRuleException(ex.getMessage()); }
        events.publishEvent(new TaskStatusChangedEvent(task.getOwner().getId(), task.getId(), before, status, workspaceId));
        workspaceService.record(workspaceId, actorId, WorkspaceEventType.TASK_STATUS_CHANGED,
                "“" + task.getTitle() + "” moved to " + status.name().toLowerCase().replace('_', ' '), now);
        automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_STATUS_CHANGED, actorId, workspaceId, taskId,
                automationData(task, before, null), causationId, executionId, chainDepth);
        if (status == TaskStatus.COMPLETED) automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_COMPLETED,
                actorId, workspaceId, taskId, automationData(task, before, null), causationId, executionId, chainDepth);
        else if (before == TaskStatus.COMPLETED) automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_REOPENED,
                actorId, workspaceId, taskId, automationData(task, before, null), causationId, executionId, chainDepth);
    }

    public void setPriorityFromAutomation(UUID actorId, UUID workspaceId, UUID taskId, TaskPriority priority,
            UUID executionId, UUID causationId, int chainDepth) {
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.EDIT_TASK);
        Task task = tasks.findLockedByIdAndWorkspace(taskId, workspaceId).orElseThrow(TaskNotFoundException::new);
        if (priority == null) throw new TaskRuleException("Choose a task priority.");
        if (task.getPriority() == priority) return;
        Instant now = clock.instant();
        task.updateDetails(task.getTitle(), task.getDescription(), priority, task.getStartDate(), task.getDueDate(), now);
        workspaceService.record(workspaceId, actorId, WorkspaceEventType.TASK_PRIORITY_CHANGED,
                "“" + task.getTitle() + "” priority changed to " + priority.name().toLowerCase(), now);
        automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_PRIORITY_CHANGED, actorId, workspaceId, taskId,
                automationData(task, null, null), causationId, executionId, chainDepth);
    }

    private Map<String, Object> automationData(Task task, TaskStatus previousStatus, UUID assigneeId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId()); data.put("title", task.getTitle()); data.put("status", task.getStatus().name());
        data.put("priority", task.getPriority().name()); data.put("dueDate", task.getDueDate());
        data.put("archived", task.getArchivedAt() != null); data.put("workspaceId", task.getWorkspaceId());
        if (previousStatus != null) data.put("previousStatus", previousStatus.name());
        if (assigneeId != null) data.put("assigneeId", assigneeId);
        return data;
    }

    private Map<String, Object> automationData(Task task, TaskStatus previousStatus) {
        return automationData(task, previousStatus, null);
    }

    public UUID comment(UUID actorId, UUID workspaceId, UUID taskId, String body, Collection<UUID> mentionedUsers) {
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.COMMENT);
        Task task = tasks.findByIdAndWorkspace(taskId, workspaceId).orElseThrow(TaskNotFoundException::new);
        String normalized = validateBody(body);
        Set<UUID> selected = eligibleMentions(workspaceId, mentionedUsers);
        Instant now = clock.instant();
        TaskComment comment = comments.saveAndFlush(TaskComment.create(workspaceId, taskId, actorId, normalized, now));
        selected.forEach(userId -> {
            mentions.save(TaskCommentMention.create(comment.getId(), workspaceId, userId));
            if (!userId.equals(actorId) && memberships.findByWorkspace_IdAndUser_Id(workspaceId, userId)
                    .map(WorkspaceMembership::isNotifyMentions).orElse(false)) notifications.save(WorkspaceNotification.deliver(workspaceId, userId, taskId,
                    WorkspaceNotificationType.MENTION, limitNotificationTitle(users.findById(actorId).orElseThrow(TaskNotFoundException::new).getDisplayName()
                            + " mentioned you on “" + task.getTitle() + "”"), now));
        });
        UserAccount author = users.findById(actorId).orElseThrow(TaskNotFoundException::new);
        workspaceService.record(workspaceId, actorId, WorkspaceEventType.COMMENT_ADDED,
                author.getDisplayName() + " commented on “" + task.getTitle() + "”", now);
        return comment.getId();
    }

    public void editComment(UUID actorId, UUID workspaceId, UUID taskId, UUID commentId, String body, Collection<UUID> mentionedUsers) {
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.COMMENT);
        TaskComment comment = comments.findLocked(commentId, workspaceId, taskId).orElseThrow(TaskNotFoundException::new);
        WorkspaceMembership member = memberships.findByWorkspace_IdAndUser_Id(workspaceId, actorId).orElseThrow(TaskNotFoundException::new);
        if (!comment.getAuthorId().equals(actorId) && member.getRole() != WorkspaceRole.OWNER && member.getRole() != WorkspaceRole.ADMIN)
            throw new WorkspaceForbiddenException();
        Set<UUID> selected = eligibleMentions(workspaceId, mentionedUsers);
        Set<UUID> previous = mentions.findAllByCommentId(commentId).stream().map(TaskCommentMention::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> newlyMentioned = new LinkedHashSet<>(selected);
        newlyMentioned.removeAll(previous); newlyMentioned.remove(actorId);
        UserAccount author = newlyMentioned.isEmpty() ? null : users.findById(actorId).orElseThrow(TaskNotFoundException::new);
        Task task = newlyMentioned.isEmpty() ? null : tasks.findByIdAndWorkspace(taskId, workspaceId).orElseThrow(TaskNotFoundException::new);
        comment.edit(validateBody(body), clock.instant()); mentions.deleteAllByCommentId(commentId);
        for (UUID userId : selected) {
            mentions.save(TaskCommentMention.create(commentId, workspaceId, userId));
            if (newlyMentioned.contains(userId) && memberships.findByWorkspace_IdAndUser_Id(workspaceId, userId)
                    .map(WorkspaceMembership::isNotifyMentions).orElse(false)) {
                notifications.save(WorkspaceNotification.deliver(workspaceId, userId, taskId,
                        WorkspaceNotificationType.MENTION,
                        limitNotificationTitle(author.getDisplayName() + " mentioned you on “" + task.getTitle() + "”"), clock.instant()));
            }
        }
    }

    public void deleteComment(UUID actorId, UUID workspaceId, UUID taskId, UUID commentId) {
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.COMMENT);
        TaskComment comment = comments.findLocked(commentId, workspaceId, taskId).orElseThrow(TaskNotFoundException::new);
        WorkspaceMembership member = memberships.findByWorkspace_IdAndUser_Id(workspaceId, actorId).orElseThrow(TaskNotFoundException::new);
        if (!comment.getAuthorId().equals(actorId) && member.getRole() != WorkspaceRole.OWNER && member.getRole() != WorkspaceRole.ADMIN)
            throw new WorkspaceForbiddenException();
        comments.delete(comment);
    }

    @Transactional(readOnly = true)
    public List<SharedComment> comments(UUID actorId, UUID workspaceId, UUID taskId) {
        task(actorId, workspaceId, taskId);
        List<TaskComment> rows = comments.findAllByWorkspaceIdAndTaskIdOrderByCreatedAtAsc(workspaceId, taskId, PageRequest.of(0, 100));
        Set<UUID> authorIds = rows.stream().map(TaskComment::getAuthorId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, String> authorNames = new LinkedHashMap<>();
        users.findAllById(authorIds).forEach(user -> authorNames.put(user.getId(), user.getDisplayName()));
        WorkspaceMembership current = memberships.findByWorkspace_IdAndUser_Id(workspaceId, actorId).orElseThrow(TaskNotFoundException::new);
        return rows.stream().map(row -> {
            List<UUID> mentioned = mentions.findAllByCommentId(row.getId()).stream().map(TaskCommentMention::getUserId).toList();
            List<String> mentionNames = mentioned.stream().map(users::findById).flatMap(java.util.Optional::stream)
                    .map(UserAccount::getDisplayName).toList();
            return new SharedComment(row.getId(), row.getAuthorId(), authorNames.getOrDefault(row.getAuthorId(), "Former member"),
                    row.getBody(), row.getCreatedAt(), row.getEditedAt(), mentionNames, mentioned,
                    row.getAuthorId().equals(actorId) || current.getRole() == WorkspaceRole.OWNER || current.getRole() == WorkspaceRole.ADMIN);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkloadMember> workload(UUID actorId, UUID workspaceId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW_WORKLOAD);
        LocalDate today = LocalDate.now(clock);
        Map<UUID, Object[]> counts = new LinkedHashMap<>();
        tasks.workspaceWorkload(workspaceId, today, today.plusDays(7), OPEN, HIGH).forEach(row -> counts.put((UUID) row[0], row));
        List<WorkloadMember> result = new ArrayList<>();
        for (WorkspaceMembership membership : memberships.findAllByWorkspace_IdAndStatusOrderByJoinedAtAsc(workspaceId, MembershipStatus.ACTIVE)) {
            Object[] row = counts.get(membership.getUser().getId());
            long open = row == null ? 0 : number(row[1]); long soon = row == null ? 0 : number(row[2]);
            long overdue = row == null ? 0 : number(row[3]); long high = row == null ? 0 : number(row[4]);
            boolean overLimit = membership.getMaxOpenTasks() != null && open > membership.getMaxOpenTasks();
            String signal = overLimit ? "Open assignments exceed this member's self-set task limit of " + membership.getMaxOpenTasks()
                    : overdue > 0 ? overdue + " overdue assignment" + (overdue == 1 ? "" : "s") : "Within the configured task-count limit";
            result.add(new WorkloadMember(membership, open, soon, overdue, high, overLimit, signal));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public long unassigned(UUID actorId, UUID workspaceId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW_WORKLOAD);
        return tasks.countUnassignedWorkspaceTasks(workspaceId, OPEN);
    }

    @Transactional(readOnly = true)
    public WorkspaceCalendarPage calendar(UUID actorId, UUID workspaceId, LocalDate requestedMonth) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        LocalDate today = LocalDate.now(clock);
        LocalDate selected = requestedMonth == null ? today.withDayOfMonth(1) : requestedMonth.withDayOfMonth(1);
        if (selected.getYear() < 1900 || selected.getYear() > 2100) selected = today.withDayOfMonth(1);
        LocalDate first = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate last = selected.with(TemporalAdjusters.lastDayOfMonth())
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        Map<LocalDate, List<Task>> byDate = new LinkedHashMap<>();
        tasks.findWorkspaceCalendarTasks(workspaceId, first, last, PageRequest.of(0, 250))
                .forEach(task -> byDate.computeIfAbsent(task.getDueDate(), ignored -> new ArrayList<>()).add(task));
        List<WorkspaceCalendarDay> days = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1))
            days.add(new WorkspaceCalendarDay(date, date.getMonth() == selected.getMonth(), date.equals(today),
                    List.copyOf(byDate.getOrDefault(date, List.of()))));
        return new WorkspaceCalendarPage(selected, selected.minusMonths(1), selected.plusMonths(1),
                selected.format(DateTimeFormatter.ofPattern("MMMM yyyy")), List.copyOf(days));
    }

    @Transactional(readOnly = true)
    public List<AssignedTask> assignedToMe(UUID actorId) {
        List<WorkspaceMembership> workspacesForUser = memberships.findAllByUser_IdAndStatusOrderByJoinedAtAsc(actorId, MembershipStatus.ACTIVE);
        List<AssignedTask> result = new ArrayList<>();
        for (WorkspaceMembership membership : workspacesForUser) {
            UUID workspaceId = membership.getWorkspace().getId();
            for (Task task : tasks.findAssignedWorkspaceTasks(workspaceId, actorId, PageRequest.of(0, 100))) {
                String projectName = placements.findByTaskIdAndOwnerId(task.getId(), task.getOwner().getId())
                        .flatMap(placement -> projects.findByIdAndWorkspace_Id(placement.getProjectId(), workspaceId))
                        .map(com.taskos.project.entity.Project::getName).orElse("Workspace task");
                result.add(new AssignedTask(membership.getWorkspace(), task, projectName));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceNotification> notifications(UUID actorId) {
        Set<UUID> accessible = memberships.findAllByUser_IdAndStatusOrderByJoinedAtAsc(actorId, MembershipStatus.ACTIVE).stream()
                .map(row -> row.getWorkspace().getId()).collect(java.util.stream.Collectors.toSet());
        return notifications.findAllByRecipientIdOrderByDeliveredAtDesc(actorId, PageRequest.of(0, 50)).stream()
                .filter(item -> accessible.contains(item.getWorkspaceId())).toList();
    }

    public void markNotificationRead(UUID actorId, UUID notificationId) {
        WorkspaceNotification item = notifications.findByIdAndRecipientId(notificationId, actorId).orElseThrow(TaskNotFoundException::new);
        authorization.require(actorId, item.getWorkspaceId(), WorkspacePermission.VIEW);
        item.markRead(clock.instant());
    }

    private Set<UUID> eligibleMentions(UUID workspaceId, Collection<UUID> requested) {
        Set<UUID> ids = requested == null ? Set.of() : new LinkedHashSet<>(requested);
        if (ids.size() > 20) throw new TaskRuleException("Mention up to 20 workspace members in one comment.");
        for (UUID userId : ids) {
            if (memberships.findByWorkspace_IdAndUser_Id(workspaceId, userId)
                    .filter(row -> row.getStatus() == MembershipStatus.ACTIVE).isEmpty())
                throw new TaskRuleException("Only active members of this workspace can be mentioned.");
        }
        return ids;
    }

    private static String validateBody(String body) {
        if (body == null || body.trim().isEmpty() || body.trim().length() > 2000)
            throw new TaskRuleException("Comment must be between 1 and 2000 characters.");
        return body.trim();
    }

    private static String limitNotificationTitle(String title) {
        return title.length() <= 160 ? title : title.substring(0, 159) + "…";
    }

    private static long number(Object value) { return value == null ? 0 : ((Number) value).longValue(); }
}
