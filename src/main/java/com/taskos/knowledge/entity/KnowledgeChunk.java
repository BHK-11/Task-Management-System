package com.taskos.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_chunks", schema = "task_os")
public class KnowledgeChunk {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private KnowledgeDocument document;
    @Column(name = "chunk_order", nullable = false, updatable = false)
    private int chunkOrder;
    @Column(nullable = false, length = 240, updatable = false)
    private String locator;
    @Column(nullable = false, length = 3000, updatable = false)
    private String content;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeChunk() {}

    public static KnowledgeChunk create(UUID ownerId, KnowledgeDocument document, int order, String locator,
            String content, Instant now) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.id = UUID.randomUUID();
        chunk.ownerId = ownerId;
        chunk.document = document;
        chunk.chunkOrder = order;
        chunk.locator = locator;
        chunk.content = content;
        chunk.createdAt = now;
        return chunk;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public KnowledgeDocument getDocument() { return document; }
    public int getChunkOrder() { return chunkOrder; }
    public String getLocator() { return locator; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
