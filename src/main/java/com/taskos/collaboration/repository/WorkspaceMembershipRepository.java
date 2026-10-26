package com.taskos.collaboration.repository;

import com.taskos.collaboration.entity.MembershipStatus;
import com.taskos.collaboration.entity.WorkspaceMembership;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    @EntityGraph(attributePaths = {"user", "workspace"})
    Optional<WorkspaceMembership> findByWorkspace_IdAndUser_Id(UUID workspaceId, UUID userId);

    @EntityGraph(attributePaths = {"user", "workspace"})
    List<WorkspaceMembership> findAllByWorkspace_IdAndStatusOrderByJoinedAtAsc(UUID workspaceId, MembershipStatus status);

    @EntityGraph(attributePaths = {"workspace"})
    List<WorkspaceMembership> findAllByUser_IdAndStatusOrderByJoinedAtAsc(UUID userId, MembershipStatus status);

    long countByWorkspace_IdAndRoleAndStatus(UUID workspaceId, com.taskos.collaboration.entity.WorkspaceRole role,
            MembershipStatus status);

    long countByWorkspace_IdAndStatus(UUID workspaceId, MembershipStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from WorkspaceMembership membership where membership.workspace.id = :workspaceId "
            + "and membership.user.id = :userId")
    Optional<WorkspaceMembership> findLocked(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    @Query("select membership from WorkspaceMembership membership where membership.workspace.id = :workspaceId "
            + "and membership.user.id = :userId and membership.status = :status")
    Optional<WorkspaceMembership> findActive(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId,
            @Param("status") MembershipStatus status);
}
