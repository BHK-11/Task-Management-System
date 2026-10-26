package com.taskos.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_checklist_items", schema = "task_os",
        indexes = @Index(name = "ix_task_checklist_order", columnList = "task_id, position, created_at"))
public class ChecklistItem {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @Column(name = "item_text", nullable = false, length = 240)
    private String text;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ChecklistItem() {
    }

    public static ChecklistItem create(Task task, String text, int position, Instant now) {
        ChecklistItem item = new ChecklistItem();
        item.id = UUID.randomUUID();
        item.task = task;
        item.text = text;
        item.position = position;
        item.createdAt = now;
        return item;
    }

    public void rename(String text) { this.text = text; }
    public void moveTo(int position) { this.position = position; }

    public boolean setCompleted(boolean completed, Instant now) {
        if (this.completed == completed) return false;
        this.completed = completed;
        this.completedAt = completed ? now : null;
        return true;
    }

    public UUID getId() { return id; }
    public Task getTask() { return task; }
    public String getText() { return text; }
    public boolean isCompleted() { return completed; }
    public int getPosition() { return position; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
