package com.taskos.knowledge.repository;

import com.taskos.knowledge.entity.KnowledgeChunk;
import com.taskos.knowledge.entity.KnowledgeIndexState;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {
    void deleteAllByDocument_Id(UUID documentId);

    @Query("select chunk from KnowledgeChunk chunk join chunk.document document "
            + "where chunk.ownerId = :ownerId and document.currentVersion = true "
            + "and document.indexState = :state and lower(chunk.content) like lower(concat('%', :query, '%'))")
    List<KnowledgeChunk> searchOwned(@Param("ownerId") UUID ownerId, @Param("state") KnowledgeIndexState state,
            @Param("query") String query, Pageable pageable);
}
