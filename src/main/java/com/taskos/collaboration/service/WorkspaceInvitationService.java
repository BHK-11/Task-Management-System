package com.taskos.collaboration.service;

import com.taskos.collaboration.dto.InvitationPreview;
import com.taskos.collaboration.entity.InvitationStatus;
import com.taskos.collaboration.entity.MembershipStatus;
import com.taskos.collaboration.entity.Workspace;
import com.taskos.collaboration.entity.WorkspaceActivity;
import com.taskos.collaboration.entity.WorkspaceEventType;
import com.taskos.collaboration.entity.WorkspaceInvitation;
import com.taskos.collaboration.entity.WorkspaceMembership;
import com.taskos.collaboration.entity.WorkspaceRole;
import com.taskos.collaboration.repository.WorkspaceActivityRepository;
import com.taskos.collaboration.repository.WorkspaceInvitationRepository;
import com.taskos.collaboration.repository.WorkspaceMembershipRepository;
import com.taskos.collaboration.repository.WorkspaceRepository;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.user.entity.AccountStatus;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkspaceInvitationService {
    private static final Duration INVITATION_LIFETIME = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final WorkspaceInvitationRepository invitations;
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceRepository workspaces;
    private final UserAccountRepository users;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceService workspaceService;

    public WorkspaceInvitationService(WorkspaceInvitationRepository invitations, WorkspaceMembershipRepository memberships,
            WorkspaceRepository workspaces, UserAccountRepository users, WorkspaceAuthorizationService authorization,
            WorkspaceService workspaceService) {
        this.invitations = invitations; this.memberships = memberships; this.workspaces = workspaces;
        this.users = users; this.authorization = authorization; this.workspaceService = workspaceService;
    }

    public String create(UUID actorId, UUID workspaceId, String email, WorkspaceRole role) {
        Workspace workspace = workspaces.findLockedById(workspaceId).orElseThrow(TaskNotFoundException::new);
        WorkspaceMembership actor = authorization.requireLocked(actorId, workspaceId, WorkspacePermission.INVITE);
        if (email == null || email.trim().length() > 254 || !email.trim().matches("(?i)^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9-]+(?:\\.[A-Z0-9-]+)+$"))
            throw new TaskRuleException("Enter a valid email address.");
        if (role == null || role == WorkspaceRole.OWNER || (actor.getRole() == WorkspaceRole.ADMIN && role == WorkspaceRole.ADMIN))
            throw new WorkspaceForbiddenException();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        boolean alreadyMember = users.findByEmail(normalizedEmail).map(UserAccount::getId)
                .flatMap(userId -> memberships.findByWorkspace_IdAndUser_Id(workspaceId, userId))
                .filter(row -> row.getStatus() == MembershipStatus.ACTIVE).isPresent();
        if (alreadyMember)
            throw new TaskRuleException("That account is already a workspace member.");
        Instant now = Instant.now();
        invitations.findAllByWorkspace_IdOrderByCreatedAtDesc(workspaceId).stream()
                .filter(row -> row.getStatus() == InvitationStatus.PENDING && row.getInvitedEmail().equalsIgnoreCase(normalizedEmail))
                .forEach(row -> {
                    if (row.getExpiresAt().isAfter(now)) throw new TaskRuleException("A pending invitation already exists for that email.");
                    row.expire();
                });
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        invitations.save(WorkspaceInvitation.create(workspace, normalizedEmail, role, actor.getUser(), hash(token), now,
                now.plus(INVITATION_LIFETIME)));
        workspaceService.record(workspaceId, actorId, WorkspaceEventType.INVITATION_CREATED,
                "An invitation was created for " + normalizedEmail, now);
        return token;
    }

    public InvitationPreview inspect(String token, String authenticatedEmail) {
        WorkspaceInvitation invitation = lookupInvitation(token);
        requireRecipient(invitation, authenticatedEmail);
        if (invitation.getStatus() != InvitationStatus.PENDING || !invitation.getExpiresAt().isAfter(Instant.now()))
            throw new TaskNotFoundException();
        return new InvitationPreview(invitation.getId(), invitation.getWorkspace().getId(),
                invitation.getWorkspace().getName(), invitation.getIntendedRole(), invitation.getExpiresAt());
    }

    public UUID accept(String token, UUID userId, String authenticatedEmail) {
        UserAccount acceptingUser = users.findLockedById(userId).filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(TaskNotFoundException::new);
        WorkspaceInvitation invitationPreview = lookupInvitation(token);
        requireRecipient(invitationPreview, authenticatedEmail);
        Workspace workspace = workspaces.findLockedById(invitationPreview.getWorkspace().getId()).orElseThrow(TaskNotFoundException::new);
        WorkspaceInvitation invitation = lockInvitation(token);
        requireRecipient(invitation, authenticatedEmail);
        Instant now = Instant.now();
        if (invitation.getStatus() != InvitationStatus.PENDING) throw new TaskRuleException("This invitation is no longer available.");
        if (!invitation.getExpiresAt().isAfter(now)) { invitation.expire(); throw new TaskRuleException("This invitation has expired."); }
        if (!workspace.getId().equals(invitation.getWorkspace().getId())) throw new TaskNotFoundException();
        if (workspace.getStatus() != com.taskos.collaboration.entity.WorkspaceStatus.ACTIVE)
            throw new TaskRuleException("This workspace is archived and cannot accept members.");
        WorkspaceMembership membership = memberships.findLocked(workspace.getId(), userId).orElse(null);
        if (membership == null) memberships.save(WorkspaceMembership.create(workspace, acceptingUser, invitation.getIntendedRole(), now));
        else if (membership.getStatus() == MembershipStatus.REMOVED) membership.reactivate(invitation.getIntendedRole(), now);
        else throw new TaskRuleException("You are already a member of this workspace.");
        invitation.accept(now);
        workspaceService.record(workspace.getId(), userId, WorkspaceEventType.MEMBER_JOINED,
                acceptingUser.getDisplayName() + " joined the workspace", now);
        return workspace.getId();
    }

    public void decline(String token, UUID userId, String authenticatedEmail) {
        WorkspaceInvitation invitation = lockInvitation(token);
        requireRecipient(invitation, authenticatedEmail);
        if (invitation.getStatus() != InvitationStatus.PENDING || !invitation.getExpiresAt().isAfter(Instant.now()))
            throw new TaskRuleException("This invitation is no longer available.");
        invitation.decline();
    }

    public void revoke(UUID actorId, UUID workspaceId, UUID invitationId) {
        workspaces.findLockedById(workspaceId).orElseThrow(TaskNotFoundException::new);
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.INVITE);
        WorkspaceInvitation invitation = invitations.findLockedByIdAndWorkspace(invitationId, workspaceId)
                .orElseThrow(TaskNotFoundException::new);
        if (invitation.getStatus() != InvitationStatus.PENDING) throw new TaskRuleException("Only pending invitations can be revoked.");
        invitation.revoke(Instant.now());
        workspaceService.record(workspaceId, actorId, WorkspaceEventType.INVITATION_REVOKED, "A pending invitation was revoked", Instant.now());
    }

    public List<WorkspaceInvitation> list(UUID actorId, UUID workspaceId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.INVITE);
        Instant now = Instant.now();
        List<WorkspaceInvitation> rows = invitations.findAllByWorkspace_IdOrderByCreatedAtDesc(workspaceId);
        rows.stream().filter(row -> row.getStatus() == InvitationStatus.PENDING && !row.getExpiresAt().isAfter(now))
                .forEach(WorkspaceInvitation::expire);
        return rows;
    }

    private WorkspaceInvitation lookupInvitation(String token) {
        if (token == null || token.length() < 40 || token.length() > 64) throw new TaskNotFoundException();
        return invitations.findByTokenHash(hash(token)).orElseThrow(TaskNotFoundException::new);
    }

    private WorkspaceInvitation lockInvitation(String token) {
        if (token == null || token.length() < 40 || token.length() > 64) throw new TaskNotFoundException();
        return invitations.findLockedByTokenHash(hash(token)).orElseThrow(TaskNotFoundException::new);
    }

    private static void requireRecipient(WorkspaceInvitation invitation, String authenticatedEmail) {
        if (authenticatedEmail == null || !invitation.getInvitedEmail().equalsIgnoreCase(authenticatedEmail))
            throw new TaskNotFoundException();
    }

    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
