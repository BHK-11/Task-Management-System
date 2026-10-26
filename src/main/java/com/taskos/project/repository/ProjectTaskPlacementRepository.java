package com.taskos.project.repository;

import com.taskos.project.entity.ProjectTaskPlacement;
import com.taskos.task.entity.TaskStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectTaskPlacementRepository extends JpaRepository<ProjectTaskPlacement, UUID> {
    @Query("""
            select distinct placement from ProjectTaskPlacement placement
            join fetch placement.task task left join fetch task.tags
            join fetch placement.boardColumn boardColumn left join fetch placement.milestone milestone
            where placement.ownerId = :ownerId and placement.projectId = :projectId
            order by boardColumn.position, placement.position
            """)
    List<ProjectTaskPlacement> findBoard(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId);

    @Query("""
            select distinct placement from ProjectTaskPlacement placement
            join fetch placement.task task left join fetch task.tags
            join fetch placement.boardColumn boardColumn left join fetch placement.milestone milestone
            where placement.taskId = :taskId and placement.ownerId = :ownerId and placement.projectId = :projectId
            """)
    Optional<ProjectTaskPlacement> findPlacement(@Param("taskId") UUID taskId,
            @Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId);

    Optional<ProjectTaskPlacement> findByTaskIdAndOwnerId(UUID taskId, UUID ownerId);

    @Query("select placement from ProjectTaskPlacement placement where placement.taskId = :taskId and placement.task.workspace.id = :workspaceId")
    Optional<ProjectTaskPlacement> findByTaskIdAndWorkspaceId(@Param("taskId") UUID taskId,
            @Param("workspaceId") UUID workspaceId);

    @Query("select distinct placement from ProjectTaskPlacement placement join fetch placement.task task "
            + "join fetch placement.boardColumn where placement.ownerId = :ownerId and placement.projectId = :projectId "
            + "order by placement.position")
    List<ProjectTaskPlacement> findAllForProject(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId);

    @Query("select distinct placement from ProjectTaskPlacement placement join fetch placement.task task "
            + "where placement.projectId = :projectId and task.workspace.id = :workspaceId order by placement.position")
    List<ProjectTaskPlacement> findAllSharedForProject(@Param("projectId") UUID projectId, @Param("workspaceId") UUID workspaceId);

    List<ProjectTaskPlacement> findAllByOwnerIdAndTaskIdIn(UUID ownerId, Collection<UUID> taskIds);

    @Query("""
            select count(placement) from ProjectTaskPlacement placement join placement.task task
            where placement.ownerId = :ownerId and placement.projectId = :projectId
              and placement.columnId = :columnId and task.archivedAt is null
              and task.status not in :terminalStatuses
              and (:excludeTaskId is null or task.id <> :excludeTaskId)
            """)
    long countWorkInColumn(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("columnId") UUID columnId, @Param("terminalStatuses") Collection<TaskStatus> terminalStatuses,
            @Param("excludeTaskId") UUID excludeTaskId);

    long countByOwnerIdAndProjectIdAndColumnId(UUID ownerId, UUID projectId, UUID columnId);

    List<ProjectTaskPlacement> findAllByOwnerIdAndProjectIdAndMilestoneId(UUID ownerId, UUID projectId, UUID milestoneId);

    @Modifying
    @Query("delete from ProjectTaskPlacement placement where placement.taskId = :taskId and placement.ownerId = :ownerId")
    int deletePlacement(@Param("taskId") UUID taskId, @Param("ownerId") UUID ownerId);
}
