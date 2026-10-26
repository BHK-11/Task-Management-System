package com.taskos.collaboration.service;

public class WorkspaceForbiddenException extends RuntimeException {
    public WorkspaceForbiddenException() { super("Your workspace role does not allow this action."); }
}
