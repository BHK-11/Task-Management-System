package com.taskos.task.service;

import com.taskos.task.dto.ChecklistItemForm;
import com.taskos.task.entity.ChecklistItem;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskActivity;
import com.taskos.task.entity.TaskActivityType;
import com.taskos.task.repository.ChecklistItemRepository;
import com.taskos.task.repository.TaskActivityRepository;
import com.taskos.task.repository.TaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChecklistService {
    private static final int MAX_ITEMS = 100;
    private final ChecklistItemRepository items;
    private final TaskRepository tasks;
    private final TaskActivityRepository activity;

    public ChecklistService(ChecklistItemRepository items, TaskRepository tasks, TaskActivityRepository activity) {
        this.items = items;
        this.tasks = tasks;
        this.activity = activity;
    }

    public UUID add(UUID ownerId, UUID taskId, ChecklistItemForm form) {
        Task task = task(ownerId, taskId);
        ensureEditable(task);
        List<ChecklistItem> existing = items.findAllByTask_IdOrderByPositionAscCreatedAtAsc(taskId);
        if (existing.size() >= MAX_ITEMS) throw new TaskRuleException("A task can have up to 100 checklist items.");
        Instant now = Instant.now();
        ChecklistItem item = ChecklistItem.create(task, normalize(form.getText()), existing.size(), now);
        items.saveAndFlush(item);
        task.touch(now);
        tasks.saveAndFlush(task);
        record(task, TaskActivityType.CHECKLIST_ADDED, "Checklist item added", now);
        return item.getId();
    }

    public void update(UUID ownerId, UUID taskId, UUID itemId, ChecklistItemForm form) {
        Task task = task(ownerId, taskId);
        ensureEditable(task);
        ChecklistItem item = items.findByIdAndTask_Id(itemId, taskId).orElseThrow(TaskNotFoundException::new);
        String text = normalize(form.getText());
        if (item.getText().equals(text)) return;
        item.rename(text);
        Instant now = Instant.now();
        items.saveAndFlush(item);
        task.touch(now);
        tasks.saveAndFlush(task);
        record(task, TaskActivityType.CHECKLIST_UPDATED, "Checklist item updated", now);
    }

    public void toggle(UUID ownerId, UUID taskId, UUID itemId) {
        Task task = task(ownerId, taskId);
        ensureEditable(task);
        ChecklistItem item = items.findByIdAndTask_Id(itemId, taskId).orElseThrow(TaskNotFoundException::new);
        boolean completing = !item.isCompleted();
        Instant now = Instant.now();
        if (!item.setCompleted(completing, now)) return;
        items.saveAndFlush(item);
        task.touch(now);
        tasks.saveAndFlush(task);
        record(task, completing ? TaskActivityType.CHECKLIST_COMPLETED : TaskActivityType.CHECKLIST_REOPENED,
                completing ? "Checklist item completed" : "Checklist item reopened", now);
    }

    public void move(UUID ownerId, UUID taskId, UUID itemId, String direction) {
        Task task = task(ownerId, taskId);
        ensureEditable(task);
        List<ChecklistItem> ordered = items.findAllByTask_IdOrderByPositionAscCreatedAtAsc(taskId);
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(itemId)) { index = i; break; }
        }
        if (index < 0) throw new TaskNotFoundException();
        if (!"UP".equals(direction) && !"DOWN".equals(direction)) {
            throw new TaskRuleException("Choose a valid reorder direction.");
        }
        int target = "UP".equals(direction) ? index - 1 : index + 1;
        if (target < 0 || target >= ordered.size()) return;
        ChecklistItem current = ordered.get(index);
        ChecklistItem adjacent = ordered.get(target);
        current.moveTo(target);
        adjacent.moveTo(index);
        Instant now = Instant.now();
        items.saveAllAndFlush(List.of(current, adjacent));
        task.touch(now);
        tasks.saveAndFlush(task);
        record(task, TaskActivityType.CHECKLIST_REORDERED, "Checklist item order changed", now);
    }

    public void delete(UUID ownerId, UUID taskId, UUID itemId) {
        Task task = task(ownerId, taskId);
        ensureEditable(task);
        ChecklistItem item = items.findByIdAndTask_Id(itemId, taskId).orElseThrow(TaskNotFoundException::new);
        items.delete(item);
        Instant now = Instant.now();
        task.touch(now);
        tasks.saveAndFlush(task);
        record(task, TaskActivityType.CHECKLIST_DELETED, "Checklist item deleted", now);
    }

    private Task task(UUID ownerId, UUID taskId) {
        return tasks.findDetailByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private void ensureEditable(Task task) {
        if (task.getArchivedAt() != null) throw new TaskRuleException("Restore this task before changing its checklist.");
    }

    private String normalize(String text) {
        if (text == null) throw new TaskRuleException("Enter a checklist item.");
        String value = text.strip().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > 240) throw new TaskRuleException("Use 1 to 240 characters.");
        return value;
    }

    private void record(Task task, TaskActivityType type, String summary, Instant at) {
        activity.save(TaskActivity.create(task.getId(), task.getOwner(), task.getOwner(), type, summary, at));
    }
}
