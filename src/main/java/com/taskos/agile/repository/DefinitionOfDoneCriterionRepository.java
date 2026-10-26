package com.taskos.agile.repository;

import com.taskos.agile.entity.DefinitionOfDoneCriterion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefinitionOfDoneCriterionRepository extends JpaRepository<DefinitionOfDoneCriterion, UUID> {
    List<DefinitionOfDoneCriterion> findAllByTaskIdOrderByPositionAsc(UUID taskId);
    List<DefinitionOfDoneCriterion> findAllByTaskIdInOrderByPositionAsc(Collection<UUID> taskIds);
    Optional<DefinitionOfDoneCriterion> findByIdAndTaskId(UUID id, UUID taskId);
    long countByTaskId(UUID taskId);
}
