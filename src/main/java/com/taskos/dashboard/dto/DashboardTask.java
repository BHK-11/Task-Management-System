package com.taskos.dashboard.dto;

import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import java.time.LocalDate;
import java.util.UUID;

public record DashboardTask(UUID id, String title, TaskPriority priority, TaskStatus status,
        LocalDate dueDate, long subtaskCount, long completedSubtaskCount, long daysOverdue, String reason) {
    public int subtaskPercent() {
        return subtaskCount == 0 ? 0 : (int) Math.round(completedSubtaskCount * 100.0 / subtaskCount);
    }

    public boolean hasSubtasks() {
        return subtaskCount > 0;
    }
}
