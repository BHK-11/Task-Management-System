package com.taskos.task.service;

public class DuplicateTaskTagException extends RuntimeException {
    public DuplicateTaskTagException() {
        super("A tag with that name already exists.");
    }
}
