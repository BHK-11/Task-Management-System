package com.taskos.agile.repository;

import com.taskos.agile.entity.SprintMembership;
import com.taskos.agile.entity.SprintMembershipStatus;
import com.taskos.agile.entity.SprintStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SprintMembershipRepository extends JpaRepository<SprintMembership, UUID> {
    List<SprintMembership> findAllBySprintIdOrderByPositionAsc(UUID sprintId);
    List<SprintMembership> findAllBySprintIdInOrderByPositionAsc(Collection<UUID> sprintIds);
    Optional<SprintMembership> findBySprintIdAndTaskId(UUID sprintId, UUID taskId);
    List<SprintMembership> findAllBySprintIdAndStatusInOrderByPositionAsc(UUID sprintId, Collection<SprintMembershipStatus> statuses);
    boolean existsBySprintIdAndTaskId(UUID sprintId, UUID taskId);
    @Query("""
            select membership from SprintMembership membership join Sprint sprint on sprint.id = membership.sprintId
            where membership.taskId = :taskId and membership.status in :memberStatuses
              and sprint.status in :sprintStatuses and (:exceptSprintId is null or sprint.id <> :exceptSprintId)
            """)
    Optional<SprintMembership> findCurrentOtherMembership(@Param("taskId") UUID taskId,
            @Param("memberStatuses") Collection<SprintMembershipStatus> memberStatuses,
            @Param("sprintStatuses") Collection<SprintStatus> sprintStatuses,
            @Param("exceptSprintId") UUID exceptSprintId);
    @Query("select membership from SprintMembership membership where membership.sprintId = :sprintId and membership.status = :status order by membership.position")
    List<SprintMembership> findBySprintIdAndStatus(@Param("sprintId") UUID sprintId, @Param("status") SprintMembershipStatus status);
    @Query("select membership from SprintMembership membership join Sprint sprint on sprint.id = membership.sprintId where sprint.projectId = :projectId and sprint.status = :sprintStatus and membership.status = :memberStatus")
    List<SprintMembership> findByProjectAndStatuses(@Param("projectId") UUID projectId,
            @Param("sprintStatus") SprintStatus sprintStatus, @Param("memberStatus") SprintMembershipStatus memberStatus);
    @Query("""
            select membership from SprintMembership membership join Sprint sprint on sprint.id = membership.sprintId
            where sprint.projectId = :projectId and membership.status in :memberStatuses and sprint.status in :sprintStatuses
            order by sprint.startDate, membership.position
            """)
    List<SprintMembership> findScheduledProjectMemberships(@Param("projectId") UUID projectId,
            @Param("memberStatuses") Collection<SprintMembershipStatus> memberStatuses,
            @Param("sprintStatuses") Collection<SprintStatus> sprintStatuses);
}
