package com.taskos.goals.repository;

import com.taskos.goals.entity.Objective;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectiveRepository extends JpaRepository<Objective, UUID> {
    List<Objective> findAllByOwnerIdAndCycleIdOrderByCreatedAtAsc(UUID ownerId, UUID cycleId);
    Optional<Objective> findByIdAndOwnerId(UUID id, UUID ownerId);
}
