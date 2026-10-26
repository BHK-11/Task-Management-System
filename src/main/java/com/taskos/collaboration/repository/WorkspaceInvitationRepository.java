package com.taskos.collaboration.repository;

import com.taskos.collaboration.entity.InvitationStatus;
import com.taskos.collaboration.entity.WorkspaceInvitation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {
    Optional<WorkspaceInvitation> findByTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"workspace", "invitedBy"})
    @Query("select invitation from WorkspaceInvitation invitation where invitation.tokenHash = :hash")
    Optional<WorkspaceInvitation> findLockedByTokenHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from WorkspaceInvitation invitation where invitation.id = :id and invitation.workspace.id = :workspaceId")
    Optional<WorkspaceInvitation> findLockedByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @EntityGraph(attributePaths = "invitedBy")
    List<WorkspaceInvitation> findAllByWorkspace_IdOrderByCreatedAtDesc(UUID workspaceId);

    boolean existsByWorkspace_IdAndInvitedEmailIgnoreCaseAndStatus(UUID workspaceId, String email, InvitationStatus status);
}
