package com.taskos.collaboration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "task_comment_mentions", schema = "task_os")
@IdClass(TaskCommentMention.Key.class)
public class TaskCommentMention {
    @Id @Column(name = "comment_id") private UUID commentId;
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    protected TaskCommentMention() { }
    public static TaskCommentMention create(UUID commentId, UUID workspaceId, UUID userId) {
        TaskCommentMention mention = new TaskCommentMention(); mention.commentId = commentId;
        mention.workspaceId = workspaceId; mention.userId = userId; return mention;
    }
    public UUID getCommentId() { return commentId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getUserId() { return userId; }
    public static final class Key implements Serializable {
        private UUID commentId;
        private UUID userId;
        public Key() { }
        public UUID getCommentId() { return commentId; }
        public UUID getUserId() { return userId; }
        @Override public boolean equals(Object other) { return other instanceof Key key && commentId.equals(key.commentId) && userId.equals(key.userId); }
        @Override public int hashCode() { return java.util.Objects.hash(commentId, userId); }
    }
}
