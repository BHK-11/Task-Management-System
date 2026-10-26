package com.taskos.project.repository;

import com.taskos.project.entity.Project;
import com.taskos.project.entity.ProjectStatus;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @Query("select project from Project project where project.id = :id and project.owner.id = :ownerId and project.workspace is null")
    Optional<Project> findByIdAndOwner_Id(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Query("select project from Project project where project.id = :id and project.owner.id = :ownerId")
    Optional<Project> findByIdAndOwner_IdIncludingShared(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Query("select project from Project project where project.owner.id = :ownerId and project.workspace is null "
            + "and project.archivedAt is null order by project.updatedAt desc")
    java.util.List<Project> findAllByOwner_IdAndArchivedAtIsNullOrderByUpdatedAtDesc(@Param("ownerId") UUID ownerId);

    @Query("select project from Project project where project.owner.id = :ownerId and project.workspace is null and project.id in :ids")
    java.util.List<Project> findAllByOwner_IdAndIdIn(@Param("ownerId") UUID ownerId, @Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from Project project where project.id = :id and project.owner.id = :ownerId and project.workspace is null")
    Optional<Project> findLockedByIdAndOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from Project project where project.id = :id and project.owner.id = :ownerId")
    Optional<Project> findLockedByIdAndOwnerIncludingShared(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from Project project where project.id = :id and project.workspace.id = :workspaceId")
    Optional<Project> findLockedByIdAndWorkspace(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Query("""
            select project from Project project
            where project.owner.id = :ownerId and project.workspace is null
              and (:status is null or project.status = :status)
              and (:archived is null or (:archived = true and project.archivedAt is not null)
                   or (:archived = false and project.archivedAt is null))
              and (:search is null or lower(project.name) like concat('%', :search, '%')
                   or lower(project.description) like concat('%', :search, '%'))
            """)
    Page<Project> search(@Param("ownerId") UUID ownerId, @Param("status") ProjectStatus status,
            @Param("archived") Boolean archived, @Param("search") String search, Pageable pageable);

    @Query("select count(project) from Project project where project.owner.id = :ownerId and project.workspace is null "
            + "and project.archivedAt is null and project.status in :statuses")
    long countByOwner_IdAndArchivedAtIsNullAndStatusIn(@Param("ownerId") UUID ownerId,
            @Param("statuses") Collection<ProjectStatus> statuses);

    java.util.List<Project> findAllByWorkspace_IdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID workspaceId);

    Optional<Project> findByIdAndWorkspace_Id(UUID id, UUID workspaceId);

    long countByWorkspace_IdAndArchivedAtIsNull(UUID workspaceId);
}
