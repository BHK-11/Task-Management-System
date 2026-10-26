package com.taskos.knowledge.repository;

import com.taskos.knowledge.entity.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    List<KnowledgeDocument> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);
    List<KnowledgeDocument> findAllByOwnerIdAndFileNameIgnoreCaseOrderByVersionDesc(UUID ownerId, String fileName);
    List<KnowledgeDocument> findAllByOwnerIdAndLogicalIdOrderByVersionDesc(UUID ownerId, UUID logicalId);
    Optional<KnowledgeDocument> findByIdAndOwnerId(UUID id, UUID ownerId);
    long countByOwnerId(UUID ownerId);
}
