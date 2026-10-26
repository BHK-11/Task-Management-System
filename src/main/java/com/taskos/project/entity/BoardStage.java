package com.taskos.project.entity;

import com.taskos.task.entity.TaskStatus;

public enum BoardStage {
    BACKLOG(TaskStatus.TODO), TODO(TaskStatus.TODO), IN_PROGRESS(TaskStatus.IN_PROGRESS),
    IN_REVIEW(TaskStatus.IN_PROGRESS), ON_HOLD(TaskStatus.ON_HOLD),
    DONE(TaskStatus.COMPLETED), CANCELLED(TaskStatus.CANCELLED);

    private final TaskStatus taskStatus;

    BoardStage(TaskStatus taskStatus) { this.taskStatus = taskStatus; }

    public TaskStatus taskStatus() { return taskStatus; }

    public static BoardStage preferredFor(TaskStatus status) {
        return switch (status) {
            case TODO -> TODO;
            case IN_PROGRESS -> IN_PROGRESS;
            case ON_HOLD -> ON_HOLD;
            case COMPLETED -> DONE;
            case CANCELLED -> CANCELLED;
        };
    }
}
