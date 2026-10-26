package com.taskos.collaboration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_comments", schema = "task_os", indexes =
        @Index(name = "ix_task_comments_timeline", columnList = "workspace_id, task_id, created_at"))
public class TaskComment {
    @Id private UUID id;
    @Column(name = "workspace_id", nullable = false, updatable = false) private UUID workspaceId;
    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(name = "author_id", nullable = false, updatable = false) private UUID authorId;
    @Column(nullable = false, length = 2000) private String body;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "edited_at") private Instant editedAt;
    @Version private Long version;
    protected TaskComment() { }
    public static TaskComment create(UUID workspaceId, UUID taskId, UUID authorId, String body, Instant now) {
        TaskComment comment = new TaskComment(); comment.id = UUID.randomUUID(); comment.workspaceId = workspaceId;
        comment.taskId = taskId; comment.authorId = authorId; comment.body = body.trim(); comment.createdAt = now; return comment;
    }
    public void edit(String body, Instant now) { this.body = body.trim(); this.editedAt = now; }
    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getTaskId() { return taskId; }
    public UUID getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
}
