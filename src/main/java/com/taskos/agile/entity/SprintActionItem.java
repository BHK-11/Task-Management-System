package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sprint_action_items", schema = "task_os")
public class SprintActionItem {
    @Id private UUID id;
    @Column(name = "sprint_id", nullable = false, updatable = false) private UUID sprintId;
    @Column(nullable = false, length = 1000) private String description;
    @Column(name = "task_id", unique = true) private UUID taskId;
    @Column(nullable = false) private int position;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected SprintActionItem() { }
    public static SprintActionItem create(UUID sprintId, String description, int position, Instant now) {
        SprintActionItem item = new SprintActionItem(); item.id = UUID.randomUUID(); item.sprintId = sprintId;
        item.description = description.trim(); item.position = position; item.createdAt = now; return item;
    }
    public void linkTask(UUID taskId) { if (this.taskId == null) this.taskId = taskId; }
    public UUID getId() { return id; }
    public UUID getSprintId() { return sprintId; }
    public String getDescription() { return description; }
    public UUID getTaskId() { return taskId; }
    public int getPosition() { return position; }
    public Instant getCreatedAt() { return createdAt; }
}
