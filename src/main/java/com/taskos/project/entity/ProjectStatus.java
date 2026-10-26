package com.taskos.project.entity;

public enum ProjectStatus {
    PLANNED, ACTIVE, ON_HOLD, COMPLETED, CANCELLED;

    public boolean canTransitionTo(ProjectStatus next) {
        return switch (this) {
            case PLANNED -> next == ACTIVE || next == ON_HOLD || next == CANCELLED;
            case ACTIVE -> next == ON_HOLD || next == COMPLETED || next == CANCELLED;
            case ON_HOLD -> next == ACTIVE || next == CANCELLED;
            case COMPLETED -> next == ACTIVE;
            case CANCELLED -> next == PLANNED;
        };
    }
}
