package com.taskos.agile.repository;

import com.taskos.agile.entity.AcceptanceCriterion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcceptanceCriterionRepository extends JpaRepository<AcceptanceCriterion, UUID> {
    List<AcceptanceCriterion> findAllByTaskIdOrderByPositionAsc(UUID taskId);
    Optional<AcceptanceCriterion> findByIdAndTaskId(UUID id, UUID taskId);
    long countByTaskId(UUID taskId);
    List<AcceptanceCriterion> findAllByTaskIdInOrderByPositionAsc(Collection<UUID> taskIds);
}
