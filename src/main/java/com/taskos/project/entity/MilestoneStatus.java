package com.taskos.project.entity;

public enum MilestoneStatus {
    PLANNED, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean canTransitionTo(MilestoneStatus next) {
        return switch (this) {
            case PLANNED -> next == IN_PROGRESS || next == COMPLETED || next == CANCELLED;
            case IN_PROGRESS -> next == PLANNED || next == COMPLETED || next == CANCELLED;
            case COMPLETED -> next == IN_PROGRESS;
            case CANCELLED -> next == PLANNED;
        };
    }
}
