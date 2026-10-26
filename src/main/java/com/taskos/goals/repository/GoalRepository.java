package com.taskos.goals.repository;

import com.taskos.goals.entity.Goal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findAllByOwnerIdAndArchivedAtIsNullOrderByTargetDateAscUpdatedAtDesc(UUID ownerId);
    List<Goal> findAllByOwnerIdAndStatusAndArchivedAtIsNull(UUID ownerId, com.taskos.goals.entity.GoalStatus status);
    Optional<Goal> findByIdAndOwnerId(UUID id, UUID ownerId);
    long countByOwnerIdAndStatusAndArchivedAtIsNull(UUID ownerId, com.taskos.goals.entity.GoalStatus status);
}
