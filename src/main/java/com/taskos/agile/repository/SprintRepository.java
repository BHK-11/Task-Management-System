package com.taskos.agile.repository;

import com.taskos.agile.entity.Sprint;
import com.taskos.agile.entity.SprintStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sprint from Sprint sprint where sprint.id = :id and sprint.projectId = :projectId")
    Optional<Sprint> findLockedByIdAndProjectId(@Param("id") UUID id, @Param("projectId") UUID projectId);
    Optional<Sprint> findByIdAndProjectId(UUID id, UUID projectId);
    List<Sprint> findAllByProjectIdOrderByStartDateDesc(UUID projectId);
    List<Sprint> findAllByProjectIdAndStatusOrderByStartDateAsc(UUID projectId, SprintStatus status);
    Optional<Sprint> findFirstByProjectIdAndStatus(UUID projectId, SprintStatus status);
    List<Sprint> findAllByProjectIdAndStatusOrderByCompletedAtDesc(UUID projectId, SprintStatus status);
    boolean existsByProjectId(UUID projectId);
}
