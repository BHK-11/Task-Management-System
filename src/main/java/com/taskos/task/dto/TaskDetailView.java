package com.taskos.task.dto;

import com.taskos.task.entity.ChecklistItem;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskActivity;
import com.taskos.task.entity.TaskStatus;
import java.util.List;
import java.time.LocalDate;

public record TaskDetailView(
        Task task,
        List<Task> subtasks,
        List<ChecklistItem> checklist,
        List<TaskActivity> activity,
        boolean overdue,
        LocalDate today) {
    public long completedSubtaskCount() {
        return subtasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
    }

    public long completedChecklistCount() {
        return checklist.stream().filter(ChecklistItem::isCompleted).count();
    }
}
