package com.taskos.task.entity;

import com.taskos.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tags", schema = "task_os",
        indexes = @Index(name = "ix_tags_owner_name", columnList = "owner_id, name"))
public class TaskTag {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @Column(nullable = false, length = 32)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskTagColor color = TaskTagColor.SLATE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskTag() {
    }

    public static TaskTag create(UserAccount owner, String name, TaskTagColor color, Instant now) {
        TaskTag tag = new TaskTag();
        tag.id = UUID.randomUUID();
        tag.owner = owner;
        tag.name = name;
        tag.color = color == null ? TaskTagColor.SLATE : color;
        tag.createdAt = now;
        return tag;
    }

    public void update(String name, TaskTagColor color) {
        this.name = name;
        this.color = color;
    }

    public UUID getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public String getName() { return name; }
    public TaskTagColor getColor() { return color; }
    public Instant getCreatedAt() { return createdAt; }
}
