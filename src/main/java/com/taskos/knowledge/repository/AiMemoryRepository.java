package com.taskos.knowledge.repository;

import com.taskos.knowledge.entity.AiMemory;
import com.taskos.knowledge.entity.AiMemoryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMemoryRepository extends JpaRepository<AiMemory, UUID> {
    List<AiMemory> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<AiMemory> findAllByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, AiMemoryStatus status);
    Optional<AiMemory> findByIdAndOwnerId(UUID id, UUID ownerId);
    long countByOwnerIdAndStatus(UUID ownerId, AiMemoryStatus status);
}
