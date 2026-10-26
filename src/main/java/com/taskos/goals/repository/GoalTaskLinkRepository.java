package com.taskos.goals.repository;

import com.taskos.goals.entity.GoalTaskLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalTaskLinkRepository extends JpaRepository<GoalTaskLink, UUID> {
    List<GoalTaskLink> findAllByOwnerIdAndGoalId(UUID ownerId, UUID goalId);
    List<GoalTaskLink> findAllByOwnerIdAndGoalIdIn(UUID ownerId, Collection<UUID> goalIds);
    Optional<GoalTaskLink> findByOwnerIdAndGoalIdAndTaskId(UUID ownerId, UUID goalId, UUID taskId);
}
