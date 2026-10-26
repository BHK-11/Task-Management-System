package com.taskos.collaboration.entity;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_memberships", schema = "task_os",
        indexes = @Index(name = "ix_workspace_memberships_user", columnList = "user_id, status, workspace_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_workspace_membership_user", columnNames = {"workspace_id", "user_id"}))
public class WorkspaceMembership {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "workspace_id", nullable = false, updatable = false)
    private Workspace workspace;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private WorkspaceRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private MembershipStatus status;
    @Column(name = "joined_at", nullable = false, updatable = false) private Instant joinedAt;
    @Column(name = "max_open_tasks") private Integer maxOpenTasks;
    @Column(name = "notify_assignments", nullable = false) private boolean notifyAssignments = true;
    @Column(name = "notify_mentions", nullable = false) private boolean notifyMentions = true;
    @Column(name = "notify_activity", nullable = false) private boolean notifyActivity;
    @Version private Long version;

    protected WorkspaceMembership() { }

    public static WorkspaceMembership create(Workspace workspace, UserAccount user, WorkspaceRole role, Instant now) {
        WorkspaceMembership membership = new WorkspaceMembership(); membership.id = UUID.randomUUID();
        membership.workspace = workspace; membership.user = user; membership.role = role;
        membership.status = MembershipStatus.ACTIVE; membership.joinedAt = now; return membership;
    }

    public void changeRole(WorkspaceRole role) { this.role = role; }
    public void remove() { this.status = MembershipStatus.REMOVED; }
    public void reactivate(WorkspaceRole role, Instant now) { this.role = role; this.status = MembershipStatus.ACTIVE; this.joinedAt = now; }
    public void setMaxOpenTasks(Integer maxOpenTasks) { this.maxOpenTasks = maxOpenTasks; }
    public void setNotificationPreferences(boolean assignments, boolean mentions, boolean activity) {
        this.notifyAssignments = assignments; this.notifyMentions = mentions; this.notifyActivity = activity;
    }
    public UUID getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public UserAccount getUser() { return user; }
    public WorkspaceRole getRole() { return role; }
    public MembershipStatus getStatus() { return status; }
    public Instant getJoinedAt() { return joinedAt; }
    public Integer getMaxOpenTasks() { return maxOpenTasks; }
    public boolean isNotifyAssignments() { return notifyAssignments; }
    public boolean isNotifyMentions() { return notifyMentions; }
    public boolean isNotifyActivity() { return notifyActivity; }
    public long getVersion() { return version == null ? 0 : version; }
}
