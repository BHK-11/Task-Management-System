package com.taskos.collaboration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_assignment_history", schema = "task_os", indexes =
        @Index(name = "ix_assignment_history_task", columnList = "workspace_id, task_id, occurred_at"))
public class TaskAssignmentHistory {
    @Id private UUID id;
    @Column(name = "workspace_id", nullable = false, updatable = false) private UUID workspaceId;
    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(name = "assignee_id", updatable = false) private UUID assigneeId;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false) private AssignmentAction action;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    protected TaskAssignmentHistory() { }
    public static TaskAssignmentHistory create(UUID workspaceId, UUID taskId, UUID assigneeId, UUID actorId,
            AssignmentAction action, Instant now) {
        TaskAssignmentHistory row = new TaskAssignmentHistory(); row.id = UUID.randomUUID(); row.workspaceId = workspaceId;
        row.taskId = taskId; row.assigneeId = assigneeId; row.actorId = actorId; row.action = action; row.occurredAt = now; return row;
    }
}
