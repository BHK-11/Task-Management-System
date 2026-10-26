package com.taskos.task.dto;

import com.taskos.task.entity.Task;
import java.time.LocalDate;

public record TaskListItem(
        Task task,
        String parentTitle,
        long subtaskCount,
        long completedSubtaskCount,
        boolean overdue) {
    public int subtaskPercent() {
        return subtaskCount == 0 ? 0 : (int) Math.round(completedSubtaskCount * 100.0 / subtaskCount);
    }

    public boolean hasDueDate() {
        return task.getDueDate() != null;
    }

    public LocalDate dueDate() {
        return task.getDueDate();
    }
}
