package com.taskos.goals.repository;

import com.taskos.goals.entity.KeyResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyResultRepository extends JpaRepository<KeyResult, UUID> {
    List<KeyResult> findAllByOwnerIdAndObjectiveIdOrderByCreatedAtAsc(UUID ownerId, UUID objectiveId);
    Optional<KeyResult> findByIdAndOwnerId(UUID id, UUID ownerId);
}
