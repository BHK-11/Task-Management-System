package com.taskos.collaboration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_assignments", schema = "task_os")
public class TaskAssignment {
    @Id @Column(name = "task_id", updatable = false) private UUID taskId;
    @Column(name = "workspace_id", nullable = false, updatable = false) private UUID workspaceId;
    @Column(name = "assignee_id", nullable = false) private UUID assigneeId;
    @Column(name = "assigned_by_id", nullable = false) private UUID assignedById;
    @Column(name = "assigned_at", nullable = false) private Instant assignedAt;
    @Version private Long version;
    protected TaskAssignment() { }
    public static TaskAssignment create(UUID workspaceId, UUID taskId, UUID assigneeId, UUID actorId, Instant now) {
        TaskAssignment row = new TaskAssignment(); row.workspaceId = workspaceId; row.taskId = taskId;
        row.assigneeId = assigneeId; row.assignedById = actorId; row.assignedAt = now; return row;
    }
    public void reassign(UUID assigneeId, UUID actorId, Instant now) {
        this.assigneeId = assigneeId; this.assignedById = actorId; this.assignedAt = now;
    }
    public UUID getTaskId() { return taskId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getAssigneeId() { return assigneeId; }
    public UUID getAssignedById() { return assignedById; }
    public Instant getAssignedAt() { return assignedAt; }
}
