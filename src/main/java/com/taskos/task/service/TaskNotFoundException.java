package com.taskos.task.service;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException() {
        super("Task was not found.");
    }
}
