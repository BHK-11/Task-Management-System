package com.taskos.task.service;

import com.taskos.agile.repository.AgileWorkItemRepository;
import com.taskos.automation.entity.AutomationTriggerType;
import com.taskos.automation.service.AutomationOutboxService;
import com.taskos.task.dto.BulkTaskAction;
import com.taskos.task.dto.BulkTaskActionForm;
import com.taskos.task.dto.TaskDetailView;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.dto.TaskListItem;
import com.taskos.task.dto.TaskSearchForm;
import com.taskos.task.dto.TaskSort;
import com.taskos.task.event.TaskStatusChangedEvent;
import com.taskos.task.event.TaskActivityRecordedEvent;
import com.taskos.task.dto.SubtaskForm;
import com.taskos.task.entity.ChecklistItem;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskActivity;
import com.taskos.task.entity.TaskActivityType;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.entity.TaskTag;
import com.taskos.task.repository.ChecklistItemRepository;
import com.taskos.task.repository.TaskActivityRepository;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.repository.TaskSpecifications;
import com.taskos.task.repository.TaskTagRepository;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskService {
    private static final int MAX_SUBTASKS = 100;
    private static final int MAX_CHECKLIST_ITEMS = 100;
    private static final int MAX_TAGS_PER_TASK = 10;

    private final TaskRepository tasks;
    private final TaskTagRepository tags;
    private final ChecklistItemRepository checklistItems;
    private final TaskActivityRepository activity;
    private final UserAccountRepository users;
    private final ApplicationEventPublisher events;
    private final AgileWorkItemRepository agileItems;
    private final AutomationOutboxService automationOutbox;

    public TaskService(TaskRepository tasks, TaskTagRepository tags,
            ChecklistItemRepository checklistItems, TaskActivityRepository activity,
            UserAccountRepository users, ApplicationEventPublisher events, AgileWorkItemRepository agileItems,
            AutomationOutboxService automationOutbox) {
        this.tasks = tasks;
        this.tags = tags;
        this.checklistItems = checklistItems;
        this.activity = activity;
        this.users = users;
        this.events = events;
        this.agileItems = agileItems;
        this.automationOutbox = automationOutbox;
    }

    @Transactional(readOnly = true)
    public Page<TaskListItem> search(UUID ownerId, TaskSearchForm filter, int pageNumber, int pageSize) {
        UserAccount owner = owner(ownerId);
        LocalDate today = LocalDate.now(ZoneId.of(owner.getTimezone()));
        TaskSort sort;
        try {
            sort = TaskSort.valueOf(filter.getSort());
        } catch (RuntimeException ex) {
            sort = TaskSort.UPDATED;
            filter.setSort(sort.name());
        }
        int safePage = Math.max(0, Math.min(pageNumber, 10000));
        int safeSize = Math.max(1, Math.min(pageSize, 50));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort(sort));
        Specification<Task> specification = TaskSpecifications.matching(ownerId, filter, today, sort);
        Page<Task> result = tasks.findAll(specification, pageable);
        if (result.isEmpty()) return new PageImpl<>(List.of(), pageable, result.getTotalElements());

        List<UUID> ids = result.getContent().stream().map(Task::getId).toList();
        Map<UUID, Task> detailed = new HashMap<>();
        tasks.findPageDetailsByOwnerAndIds(ownerId, ids).forEach(task -> detailed.put(task.getId(), task));

        Map<UUID, long[]> children = new HashMap<>();
        List<UUID> rootIds = result.getContent().stream()
                .filter(task -> task.getParentId() == null)
                .map(Task::getId).toList();
        if (!rootIds.isEmpty()) {
            for (Object[] row : tasks.countChildrenByParentIds(ownerId, rootIds)) {
                UUID parentId = (UUID) row[0];
                TaskStatus status = (TaskStatus) row[1];
                long count = ((Number) row[2]).longValue();
                long[] values = children.computeIfAbsent(parentId, ignored -> new long[2]);
                values[0] += count;
                if (status == TaskStatus.COMPLETED) values[1] += count;
            }
        }

        List<TaskListItem> content = result.getContent().stream().map(original -> {
            Task task = detailed.getOrDefault(original.getId(), original);
            long[] counts = children.getOrDefault(task.getId(), new long[2]);
            String parentTitle = task.getParent() == null ? null : task.getParent().getTitle();
            return new TaskListItem(task, parentTitle, counts[0], counts[1], overdue(task, today));
        }).toList();
        return new PageImpl<>(content, pageable, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TaskDetailView detail(UUID ownerId, UUID taskId) {
        Task task = task(ownerId, taskId);
        UserAccount owner = task.getOwner();
        LocalDate today = LocalDate.now(ZoneId.of(owner.getTimezone()));
        List<Task> subtasks = task.getParentId() == null
                ? tasks.findByOwner_IdAndParent_IdOrderBySubtaskPositionAscCreatedAtAsc(ownerId, taskId)
                : List.of();
        List<ChecklistItem> checklist = checklistItems.findAllByTask_IdOrderByPositionAscCreatedAtAsc(taskId);
        List<TaskActivity> events = activity.findAllByOwner_IdAndTaskIdOrderByOccurredAtDesc(
                ownerId, taskId, PageRequest.of(0, 30));
        return new TaskDetailView(task, subtasks, checklist, events, overdue(task, today), today);
    }

    public UUID create(UUID ownerId, TaskForm form) {
        return create(ownerId, form, null, null, 0);
    }

    public UUID createFromAutomation(UUID ownerId, TaskForm form, UUID executionId, UUID causationId, int chainDepth) {
        return create(ownerId, form, executionId, causationId, chainDepth);
    }

    private UUID create(UUID ownerId, TaskForm form, UUID executionId, UUID causationId, int chainDepth) {
        validateDates(form.getStartDate(), form.getDueDate());
        Instant now = Instant.now();
        UserAccount owner = owner(ownerId);
        Task task = Task.create(owner, normalizeTitle(form.getTitle()), cleanDescription(form.getDescription()),
                form.getStatus(), form.getPriority(), form.getStartDate(), form.getDueDate(), null, now);
        Set<TaskTag> taskTags = resolveTags(ownerId, form.getTagIds());
        taskTags.forEach(tag -> task.assignTag(tag, now));
        tasks.saveAndFlush(task);
        record(task, TaskActivityType.TASK_CREATED, "Task created", now);
        automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_CREATED, ownerId, null, task.getId(),
                automationData(task, null), causationId, executionId, chainDepth);
        if (task.getStatus() == TaskStatus.COMPLETED) {
            record(task, TaskActivityType.TASK_COMPLETED, "Task marked complete", now);
            automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_COMPLETED, ownerId, null, task.getId(),
                    automationData(task, null), causationId, executionId, chainDepth);
        }
        return task.getId();
    }

    public void update(UUID ownerId, UUID taskId, TaskForm form) {
        validateDates(form.getStartDate(), form.getDueDate());
        Task task = task(ownerId, taskId);
        if (task.getVersion() != form.getVersion()) throw new TaskConflictException();
        if (task.getArchivedAt() != null) throw new TaskRuleException("Restore this task before editing it.");

        String title = normalizeTitle(form.getTitle());
        String description = cleanDescription(form.getDescription());
        Set<TaskTag> desiredTags = resolveTags(ownerId, form.getTagIds());
        Instant now = Instant.now();
        boolean detailsChanged = !task.getTitle().equals(title)
                || !task.getDescription().equals(description)
                || task.getPriority() != form.getPriority()
                || !java.util.Objects.equals(task.getStartDate(), form.getStartDate())
                || !java.util.Objects.equals(task.getDueDate(), form.getDueDate());
        boolean statusChanged = task.getStatus() != form.getStatus();
        boolean priorityChanged = task.getPriority() != form.getPriority();
        boolean dueDateChanged = !java.util.Objects.equals(task.getDueDate(), form.getDueDate());

        if (detailsChanged) task.updateDetails(title, description, form.getPriority(), form.getStartDate(), form.getDueDate(), now);
        if (statusChanged) changeStatus(task, form.getStatus(), now);

        Set<UUID> desiredIds = desiredTags.stream().map(TaskTag::getId).collect(java.util.stream.Collectors.toSet());
        for (TaskTag current : task.getTags()) {
            if (!desiredIds.contains(current.getId())) {
                task.removeTag(current, now);
                record(task, TaskActivityType.TAG_REMOVED, "Tag removed", now);
            }
        }
        Set<UUID> currentIds = task.getTags().stream().map(TaskTag::getId).collect(java.util.stream.Collectors.toSet());
        for (TaskTag tag : desiredTags) {
            if (!currentIds.contains(tag.getId())) {
                task.assignTag(tag, now);
                record(task, TaskActivityType.TAG_ASSIGNED, "Tag assigned", now);
                automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_TAG_ADDED, ownerId, null, task.getId(),
                        automationData(task, null), null, null, 0);
            }
        }

        if (detailsChanged) {
            String summary = dueDateChanged ? "Task details and due date updated" : "Task details updated";
            record(task, TaskActivityType.TASK_UPDATED, summary, now);
        }
        if (priorityChanged) record(task, TaskActivityType.PRIORITY_CHANGED, "Priority changed to " + form.getPriority().name(), now);
        flush(task);
        if (priorityChanged) automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_PRIORITY_CHANGED,
                ownerId, null, task.getId(), automationData(task, null), null, null, 0);
        if (dueDateChanged) automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_DUE_DATE_CHANGED,
                ownerId, null, task.getId(), automationData(task, null), null, null, 0);
    }

    public UUID createSubtask(UUID ownerId, UUID parentId, SubtaskForm form) {
        Task parent = lockedTask(ownerId, parentId);
        if (parent.getParentId() != null) throw new TaskRuleException("Subtasks can be added one level deep.");
        if (parent.getArchivedAt() != null) throw new TaskRuleException("Restore the parent task before adding a subtask.");
        if (tasks.countByOwner_IdAndParent_Id(ownerId, parentId) >= MAX_SUBTASKS) {
            throw new TaskRuleException("A task can have up to 100 subtasks.");
        }
        Instant now = Instant.now();
        Task child = Task.create(parent.getOwner(), normalizeTitle(form.getTitle()), "",
                TaskStatus.TODO, form.getPriority(), null, null, parent, now);
        int position = tasks.findByOwner_IdAndParent_IdOrderBySubtaskPositionAscCreatedAtAsc(ownerId, parentId)
                .stream().mapToInt(item -> item.getSubtaskPosition() == null ? -1 : item.getSubtaskPosition())
                .max().orElse(-1) + 1;
        child.moveSubtaskTo(position, now);
        tasks.saveAndFlush(child);
        record(child, TaskActivityType.TASK_CREATED, "Subtask created", now);
        automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_CREATED, ownerId, null, child.getId(),
                automationData(child, null), null, null, 0);
        record(parent, TaskActivityType.SUBTASK_ADDED, "Subtask added", now);
        parent.touch(now);
        tasks.saveAndFlush(parent);
        return child.getId();
    }

    public void updateSubtask(UUID ownerId, UUID parentId, UUID subtaskId, SubtaskForm form) {
        Task parent = task(ownerId, parentId);
        Task child = task(ownerId, subtaskId);
        if (!parentId.equals(child.getParentId())) throw new TaskNotFoundException();
        if (child.getArchivedAt() != null) throw new TaskRuleException("Restore this subtask before editing it.");
        String title = normalizeTitle(form.getTitle());
        Instant now = Instant.now();
        boolean changed = !child.getTitle().equals(title) || child.getPriority() != form.getPriority();
        if (!changed) return;
        child.updateDetails(title, child.getDescription(), form.getPriority(), child.getStartDate(), child.getDueDate(), now);
        record(child, TaskActivityType.TASK_UPDATED, "Subtask details updated", now);
        tasks.saveAndFlush(child);
        parent.touch(now);
        tasks.saveAndFlush(parent);
    }

    public void deleteSubtask(UUID ownerId, UUID parentId, UUID subtaskId) {
        Task parent = task(ownerId, parentId);
        Task child = task(ownerId, subtaskId);
        if (!parentId.equals(child.getParentId())) throw new TaskNotFoundException();
        deleteOwned(ownerId, List.of(child));
        Instant now = Instant.now();
        parent.touch(now);
        tasks.saveAndFlush(parent);
        record(parent, TaskActivityType.SUBTASK_DELETED, "Subtask deleted", now);
    }

    public void moveSubtask(UUID ownerId, UUID parentId, UUID subtaskId, String direction) {
        Task parent = lockedTask(ownerId, parentId);
        if (parent.getParentId() != null || parent.getArchivedAt() != null)
            throw new TaskRuleException("Only active parent tasks can reorder subtasks.");
        List<Task> children = tasks.findByOwner_IdAndParent_IdOrderBySubtaskPositionAscCreatedAtAsc(ownerId, parentId);
        int from = java.util.stream.IntStream.range(0, children.size())
                .filter(index -> children.get(index).getId().equals(subtaskId)).findFirst()
                .orElseThrow(TaskNotFoundException::new);
        int to = switch (direction == null ? "" : direction) {
            case "UP" -> from - 1;
            case "DOWN" -> from + 1;
            default -> throw new TaskRuleException("Choose whether to move the subtask up or down.");
        };
        if (to < 0 || to >= children.size()) return;
        Task child = children.get(from);
        Task neighbor = children.get(to);
        if (child.getArchivedAt() != null || neighbor.getArchivedAt() != null)
            throw new TaskRuleException("Archived subtasks cannot be reordered.");
        Instant now = Instant.now();
        child.moveSubtaskTo(to, now);
        neighbor.moveSubtaskTo(from, now);
        tasks.saveAllAndFlush(List.of(child, neighbor));
        parent.touch(now);
        tasks.saveAndFlush(parent);
    }
    public void transition(UUID ownerId, UUID taskId, TaskStatus next) {
        transition(ownerId, taskId, next, null, null, 0);
    }

    public void transitionFromAutomation(UUID ownerId, UUID taskId, TaskStatus next, UUID executionId,
            UUID causationId, int chainDepth) {
        transition(ownerId, taskId, next, executionId, causationId, chainDepth);
    }

    public void setPriorityFromAutomation(UUID ownerId, UUID taskId, TaskPriority priority, UUID executionId,
            UUID causationId, int chainDepth) {
        Task task = task(ownerId, taskId);
        if (task.getArchivedAt() != null) throw new TaskRuleException("Restore this task before changing its priority.");
        if (priority == null) throw new TaskRuleException("Choose a task priority.");
        if (task.getPriority() == priority) return;
        Instant now = Instant.now();
        task.updateDetails(task.getTitle(), task.getDescription(), priority, task.getStartDate(), task.getDueDate(), now);
        record(task, TaskActivityType.PRIORITY_CHANGED, "Priority changed to " + priority.name(), now);
        flush(task);
        automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_PRIORITY_CHANGED, ownerId, task.getWorkspaceId(),
                taskId, automationData(task, null), causationId, executionId, chainDepth);
    }

    private void transition(UUID ownerId, UUID taskId, TaskStatus next, UUID executionId, UUID causationId, int chainDepth) {
        Task task = task(ownerId, taskId);
        if (task.getArchivedAt() != null) throw new TaskRuleException("Restore this task before changing its status.");
        changeStatus(task, next, Instant.now(), executionId, causationId, chainDepth);
        flush(task);
    }

    public void transitionSubtask(UUID ownerId, UUID parentId, UUID subtaskId, TaskStatus next) {
        Task parent = task(ownerId, parentId);
        Task child = task(ownerId, subtaskId);
        if (!parentId.equals(child.getParentId())) throw new TaskNotFoundException();
        transition(ownerId, subtaskId, next);
        parent.touch(Instant.now());
        tasks.saveAndFlush(parent);
    }

    public void archive(UUID ownerId, UUID taskId) {
        List<Task> affected = hierarchy(ownerId, List.of(taskId));
        Instant now = Instant.now();
        for (Task task : affected) {
            if (task.archive(now)) record(task, TaskActivityType.TASK_ARCHIVED, "Task archived", now);
        }
        tasks.saveAllAndFlush(affected);
    }

    public void restore(UUID ownerId, UUID taskId) {
        List<Task> affected = hierarchy(ownerId, List.of(taskId));
        Instant now = Instant.now();
        for (Task task : affected) {
            if (task.restore(now)) record(task, TaskActivityType.TASK_RESTORED, "Task restored", now);
        }
        tasks.saveAllAndFlush(affected);
    }

    public void delete(UUID ownerId, UUID taskId) {
        deleteOwned(ownerId, hierarchy(ownerId, List.of(taskId)));
    }

    public int bulk(UUID ownerId, BulkTaskActionForm form) {
        List<UUID> ids = distinctIds(form.getTaskIds());
        List<Task> selected = ownedTasks(ownerId, ids);
        selected.sort(Comparator.comparing(Task::getId));
        if (selected.size() != ids.size()) throw new TaskNotFoundException();
        Instant now = Instant.now();
        switch (form.getAction()) {
            case COMPLETE -> selected.forEach(task -> changeStatus(task, TaskStatus.COMPLETED, now));
            case SET_STATUS -> {
                if (form.getStatus() == null) throw new TaskRuleException("Choose a status for the selected tasks.");
                selected.forEach(task -> changeStatus(task, form.getStatus(), now));
            }
            case SET_PRIORITY -> setPriorities(selected, form.getPriority(), now);
            case ARCHIVE -> archiveSelected(ownerId, selected, now);
            case RESTORE -> restoreSelected(ownerId, selected, now);
            case DELETE -> deleteOwned(ownerId, hierarchy(ownerId, ids));
            case ADD_TAG -> updateTags(selected, form.getTagId(), true, ownerId, now);
            case REMOVE_TAG -> updateTags(selected, form.getTagId(), false, ownerId, now);
        }
        if (form.getAction() == BulkTaskAction.COMPLETE || form.getAction() == BulkTaskAction.SET_STATUS
                || form.getAction() == BulkTaskAction.SET_PRIORITY) tasks.saveAllAndFlush(selected);
        return ids.size();
    }

    private void setPriorities(List<Task> selected, TaskPriority priority, Instant now) {
        if (priority == null) throw new TaskRuleException("Choose a priority for the selected tasks.");
        for (Task task : selected) {
            if (task.getArchivedAt() != null) throw new TaskRuleException("Restore archived tasks before changing them.");
            if (task.getPriority() == priority) continue;
            task.updateDetails(task.getTitle(), task.getDescription(), priority, task.getStartDate(), task.getDueDate(), now);
            record(task, TaskActivityType.PRIORITY_CHANGED, "Priority changed to " + priority.name(), now);
            automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_PRIORITY_CHANGED, task.getOwner().getId(),
                    null, task.getId(), automationData(task, null), null, null, 0);
        }
    }

    private void updateTags(List<Task> selected, UUID tagId, boolean assign, UUID ownerId, Instant now) {
        if (tagId == null) throw new TaskRuleException("Choose a tag.");
        TaskTag tag = tags.findByIdAndOwner_Id(tagId, ownerId).orElseThrow(TaskNotFoundException::new);
        for (Task task : selected) {
            if (task.getArchivedAt() != null) throw new TaskRuleException("Restore archived tasks before changing tags.");
            if (assign && task.getTags().size() >= MAX_TAGS_PER_TASK && !task.hasTag(tagId)) {
                throw new TaskRuleException("A task can have up to 10 tags.");
            }
            boolean changed = assign ? task.assignTag(tag, now) : task.removeTag(tag, now);
            if (changed) record(task, assign ? TaskActivityType.TAG_ASSIGNED : TaskActivityType.TAG_REMOVED,
                    assign ? "Tag assigned" : "Tag removed", now);
        }
        tasks.saveAllAndFlush(selected);
    }

    private void archiveSelected(UUID ownerId, List<Task> selected, Instant now) {
        List<Task> affected = hierarchy(ownerId, selected.stream().map(Task::getId).toList());
        for (Task task : affected) {
            if (task.archive(now)) record(task, TaskActivityType.TASK_ARCHIVED, "Task archived", now);
        }
        tasks.saveAllAndFlush(affected);
    }

    private void restoreSelected(UUID ownerId, List<Task> selected, Instant now) {
        List<Task> affected = hierarchy(ownerId, selected.stream().map(Task::getId).toList());
        for (Task task : affected) {
            if (task.restore(now)) record(task, TaskActivityType.TASK_RESTORED, "Task restored", now);
        }
        tasks.saveAllAndFlush(affected);
    }

    private void deleteOwned(UUID ownerId, List<Task> all) {
        if (agileItems.existsByTaskIdIn(all.stream().map(Task::getId).toList()))
            throw new TaskRuleException("This task has Scrum history. Archive it instead of deleting it.");
        Instant now = Instant.now();
        for (Task task : all) record(task, TaskActivityType.TASK_DELETED, "Task permanently deleted", now);
        List<Task> childrenFirst = all.stream()
                .sorted(Comparator.comparing(task -> task.getParentId() == null))
                .toList();
        tasks.deleteAll(childrenFirst);
        tasks.flush();
    }

    private List<Task> hierarchy(UUID ownerId, Collection<UUID> ids) {
        List<Task> selected = ownedTasks(ownerId, distinctIds(ids));
        if (selected.size() != new HashSet<>(ids).size()) throw new TaskNotFoundException();
        List<UUID> rootIds = selected.stream().filter(task -> task.getParentId() == null).map(Task::getId).toList();
        if (rootIds.isEmpty()) return selected;
        Map<UUID, Task> result = new LinkedHashMap<>();
        selected.forEach(task -> result.put(task.getId(), task));
        tasks.findAllByOwner_IdAndParent_IdIn(ownerId, rootIds).forEach(task -> result.put(task.getId(), task));
        return new ArrayList<>(result.values());
    }

    private List<Task> ownedTasks(UUID ownerId, Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return tasks.findAllByOwner_IdAndIdIn(ownerId, ids);
    }

    private List<UUID> distinctIds(Collection<UUID> input) {
        if (input == null || input.isEmpty()) throw new TaskRuleException("Select at least one task.");
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(input));
        if (ids.size() != input.size()) throw new TaskRuleException("The task selection contains duplicates.");
        if (ids.size() > 50) throw new TaskRuleException("Update no more than 50 tasks at a time.");
        return ids;
    }

    private void changeStatus(Task task, TaskStatus next, Instant now) {
        changeStatus(task, next, now, null, null, 0);
    }

    private void changeStatus(Task task, TaskStatus next, Instant now, UUID executionId, UUID causationId, int chainDepth) {
        if (task.getArchivedAt() != null) throw new TaskRuleException("Restore archived tasks before changing their status.");
        TaskStatus previous = task.getStatus();
        try {
            if (!task.changeStatus(next, now)) return;
        } catch (IllegalArgumentException ex) {
            throw new TaskRuleException(ex.getMessage());
        }
        if (next == TaskStatus.COMPLETED) record(task, TaskActivityType.TASK_COMPLETED, "Task marked complete", now);
        else if (previous == TaskStatus.COMPLETED) record(task, TaskActivityType.TASK_REOPENED, "Task reopened", now);
        else record(task, TaskActivityType.STATUS_CHANGED, "Status changed to " + next.name(), now);
        flush(task);
        events.publishEvent(new TaskStatusChangedEvent(task.getOwner().getId(), task.getId(), previous, next));
        automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_STATUS_CHANGED, task.getOwner().getId(),
                task.getWorkspaceId(), task.getId(), automationData(task, previous), causationId, executionId, chainDepth);
        if (next == TaskStatus.COMPLETED) automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_COMPLETED,
                task.getOwner().getId(), task.getWorkspaceId(), task.getId(), automationData(task, previous), causationId, executionId, chainDepth);
        else if (previous == TaskStatus.COMPLETED) automationOutbox.publishTaskEvent(AutomationTriggerType.TASK_REOPENED,
                task.getOwner().getId(), task.getWorkspaceId(), task.getId(), automationData(task, previous), causationId, executionId, chainDepth);
    }

    private Map<String, Object> automationData(Task task, TaskStatus previousStatus) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId()); data.put("title", task.getTitle()); data.put("status", task.getStatus().name());
        data.put("priority", task.getPriority().name()); data.put("dueDate", task.getDueDate());
        data.put("archived", task.getArchivedAt() != null); data.put("workspaceId", task.getWorkspaceId());
        if (previousStatus != null) data.put("previousStatus", previousStatus.name());
        return data;
    }

    private void flush(Task task) {
        try {
            tasks.saveAndFlush(task);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new TaskConflictException();
        }
    }

    private Set<TaskTag> resolveTags(UUID ownerId, List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return Set.of();
        if (tagIds.size() > MAX_TAGS_PER_TASK) throw new TaskRuleException("A task can have up to 10 tags.");
        Set<UUID> unique = new LinkedHashSet<>(tagIds);
        if (unique.size() != tagIds.size()) throw new TaskRuleException("Remove duplicate tags.");
        List<TaskTag> found = tags.findAllByOwner_IdAndIdIn(ownerId, unique);
        if (found.size() != unique.size()) throw new TaskNotFoundException();
        return new LinkedHashSet<>(found);
    }

    private Task task(UUID ownerId, UUID id) {
        return tasks.findDetailByIdAndOwner(id, ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private Task lockedTask(UUID ownerId, UUID id) {
        return tasks.findLockedByIdAndOwner(id, ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private UserAccount owner(UUID ownerId) {
        return users.findById(ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private void record(Task task, TaskActivityType type, String summary, Instant at) {
        activity.save(TaskActivity.create(task.getId(), task.getOwner(), task.getOwner(), type, summary, at));
        events.publishEvent(new TaskActivityRecordedEvent(task.getOwner().getId(), task.getId(), type, summary));
    }

    private boolean overdue(Task task, LocalDate today) {
        return task.getDueDate() != null && task.getDueDate().isBefore(today)
                && task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELLED;
    }

    private Sort sort(TaskSort sort) {
        return switch (sort) {
            case UPDATED -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
            case CREATED_DESC -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case CREATED_ASC -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case DUE_ASC -> Sort.by(Sort.Order.asc("dueDate").nullsLast(), Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));
            case DUE_DESC -> Sort.by(Sort.Order.desc("dueDate").nullsLast(), Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));
            case TITLE -> Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"));
            case COMPLETED -> Sort.by(Sort.Order.desc("completedAt").nullsLast(), Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));
            case PRIORITY -> Sort.unsorted();
        };
    }

    private String normalizeTitle(String title) {
        if (title == null) throw new TaskRuleException("Enter a task title.");
        String normalized = title.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > 160) throw new TaskRuleException("Use a title between 1 and 160 characters.");
        return normalized;
    }

    private String cleanDescription(String description) {
        if (description == null) return "";
        if (description.length() > 6000) throw new TaskRuleException("Description must be 6,000 characters or fewer.");
        return description.strip();
    }

    private void validateDates(LocalDate startDate, LocalDate dueDate) {
        if (startDate != null && dueDate != null && dueDate.isBefore(startDate)) {
            throw new TaskRuleException("The due date must be on or after the start date.");
        }
    }
}
