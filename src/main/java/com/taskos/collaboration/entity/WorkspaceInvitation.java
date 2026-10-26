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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_invitations", schema = "task_os", indexes =
        @Index(name = "ix_workspace_invitations_pending", columnList = "workspace_id, invited_email, status, expires_at"))
public class WorkspaceInvitation {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "workspace_id", nullable = false, updatable = false)
    private Workspace workspace;
    @Column(name = "invited_email", nullable = false, length = 254, updatable = false) private String invitedEmail;
    @Enumerated(EnumType.STRING) @Column(name = "intended_role", nullable = false, length = 16, updatable = false)
    private WorkspaceRole intendedRole;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invited_by_id", nullable = false, updatable = false)
    private UserAccount invitedBy;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private InvitationStatus status;
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Version private Long version;

    protected WorkspaceInvitation() { }

    public static WorkspaceInvitation create(Workspace workspace, String email, WorkspaceRole role, UserAccount inviter,
            String tokenHash, Instant now, Instant expiresAt) {
        WorkspaceInvitation invite = new WorkspaceInvitation(); invite.id = UUID.randomUUID(); invite.workspace = workspace;
        invite.invitedEmail = email; invite.intendedRole = role; invite.invitedBy = inviter; invite.tokenHash = tokenHash;
        invite.status = InvitationStatus.PENDING; invite.createdAt = now; invite.expiresAt = expiresAt; return invite;
    }
    public void accept(Instant now) { status = InvitationStatus.ACCEPTED; acceptedAt = now; }
    public void decline() { status = InvitationStatus.DECLINED; }
    public void revoke(Instant now) { status = InvitationStatus.REVOKED; revokedAt = now; }
    public void expire() { status = InvitationStatus.EXPIRED; }
    public UUID getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public String getInvitedEmail() { return invitedEmail; }
    public WorkspaceRole getIntendedRole() { return intendedRole; }
    public UserAccount getInvitedBy() { return invitedBy; }
    public InvitationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
