package com.taskos.ai.repository;

import com.taskos.ai.entity.AiArtifact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiArtifactRepository extends JpaRepository<AiArtifact, UUID> {
    Optional<AiArtifact> findByIdAndOwner_Id(UUID id, UUID ownerId);
    List<AiArtifact> findAllByPlan_IdAndOwner_IdOrderByCreatedAtAsc(UUID planId, UUID ownerId);
}
