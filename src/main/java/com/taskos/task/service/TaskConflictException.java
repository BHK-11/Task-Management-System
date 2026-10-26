package com.taskos.task.service;

public class TaskConflictException extends RuntimeException {
    public TaskConflictException() {
        super("This task changed in another window. Reload the latest version before saving.");
    }
}
