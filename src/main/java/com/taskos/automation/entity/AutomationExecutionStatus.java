package com.taskos.automation.entity;

public enum AutomationExecutionStatus {
    PENDING, RUNNING, WAITING_APPROVAL, SUCCEEDED, FAILED_RETRYABLE,
    FAILED_PERMANENT, DEAD_LETTER, REJECTED, EXPIRED, CANCELLED
}
