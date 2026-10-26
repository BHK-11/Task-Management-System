package com.taskos.collaboration.service;

import com.taskos.collaboration.entity.MembershipStatus;
import com.taskos.collaboration.entity.WorkspaceMembership;
import com.taskos.collaboration.entity.WorkspaceRole;
import com.taskos.collaboration.entity.WorkspaceStatus;
import com.taskos.collaboration.repository.WorkspaceMembershipRepository;
import com.taskos.collaboration.repository.WorkspaceRepository;
import com.taskos.task.service.TaskNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class WorkspaceAuthorizationService {
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceRepository workspaces;

    public WorkspaceAuthorizationService(WorkspaceMembershipRepository memberships, WorkspaceRepository workspaces) {
        this.memberships = memberships; this.workspaces = workspaces;
    }

    @Transactional(readOnly = true)
    public WorkspaceMembership require(UUID actorId, UUID workspaceId, WorkspacePermission permission) {
        WorkspaceMembership membership = memberships.findByWorkspace_IdAndUser_Id(workspaceId, actorId)
                .filter(row -> row.getStatus() == MembershipStatus.ACTIVE).orElseThrow(TaskNotFoundException::new);
        return verify(membership, permission);
    }

    @Transactional
    public WorkspaceMembership requireLocked(UUID actorId, UUID workspaceId, WorkspacePermission permission) {
        workspaces.findLockedById(workspaceId).orElseThrow(TaskNotFoundException::new);
        WorkspaceMembership membership = memberships.findLocked(workspaceId, actorId)
                .filter(row -> row.getStatus() == MembershipStatus.ACTIVE).orElseThrow(TaskNotFoundException::new);
        return verify(membership, permission);
    }

    private WorkspaceMembership verify(WorkspaceMembership membership, WorkspacePermission permission) {
        if (membership.getWorkspace().getStatus() == WorkspaceStatus.ARCHIVED && permission != WorkspacePermission.VIEW)
            throw new WorkspaceForbiddenException();
        if (!allows(membership.getRole(), permission)) throw new WorkspaceForbiddenException();
        return membership;
    }

    public static boolean allows(WorkspaceRole role, WorkspacePermission permission) {
        return switch (permission) {
            case VIEW, VIEW_WORKLOAD -> true;
            case EDIT_SETTINGS, INVITE, SHARE_PROJECT -> role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN;
            case MANAGE_MEMBERS -> role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN;
            case EDIT_TASK, ASSIGN_TASK, COMMENT -> role != WorkspaceRole.VIEWER;
        };
    }
}
