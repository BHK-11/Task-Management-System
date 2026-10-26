package com.taskos.scheduling.repository;

import com.taskos.scheduling.entity.TaskDependency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {
    List<TaskDependency> findAllByOwnerIdAndProjectIdOrderByCreatedAtAsc(UUID ownerId, UUID projectId);
    Optional<TaskDependency> findByIdAndOwnerIdAndProjectId(UUID id, UUID ownerId, UUID projectId);
    boolean existsByOwnerIdAndProjectIdAndPredecessorTaskIdAndSuccessorTaskId(
            UUID ownerId, UUID projectId, UUID predecessorTaskId, UUID successorTaskId);
}
