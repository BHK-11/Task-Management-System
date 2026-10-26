package com.taskos.goals.entity;

public enum GoalStatus {
    DRAFT, ACTIVE, PAUSED, COMPLETED, CANCELLED;

    public boolean canTransitionTo(GoalStatus next) {
        return switch (this) {
            case DRAFT -> next == ACTIVE || next == CANCELLED;
            case ACTIVE -> next == PAUSED || next == COMPLETED || next == CANCELLED;
            case PAUSED -> next == ACTIVE || next == CANCELLED;
            case COMPLETED -> next == ACTIVE;
            case CANCELLED -> next == ACTIVE;
        };
    }
}
