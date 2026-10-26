package com.taskos.goals.repository;

import com.taskos.goals.entity.GoalMilestone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalMilestoneRepository extends JpaRepository<GoalMilestone, UUID> {
    List<GoalMilestone> findAllByOwnerIdAndGoalIdOrderByPositionAsc(UUID ownerId, UUID goalId);
    List<GoalMilestone> findAllByOwnerIdAndGoalIdInOrderByGoalIdAscPositionAsc(UUID ownerId, Collection<UUID> goalIds);
    Optional<GoalMilestone> findByIdAndOwnerIdAndGoalId(UUID id, UUID ownerId, UUID goalId);
}
