package com.taskos.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_documents", schema = "task_os")
public class KnowledgeDocument {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;
    @Column(name = "logical_id", nullable = false, updatable = false)
    private UUID logicalId;
    @Column(nullable = false, updatable = false)
    private int version;
    @Column(name = "file_name", nullable = false, length = 128, updatable = false)
    private String fileName;
    @Column(nullable = false, length = 8, updatable = false)
    private String format;
    @Column(name = "content_type", nullable = false, length = 128, updatable = false)
    private String contentType;
    @Column(name = "storage_key", nullable = false, length = 80, unique = true, updatable = false)
    private String storageKey;
    @Column(nullable = false, updatable = false)
    private long size;
    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;
    @Enumerated(EnumType.STRING)
    @Column(name = "index_state", nullable = false, length = 16)
    private KnowledgeIndexState indexState;
    @Column(name = "current_version", nullable = false)
    private boolean currentVersion;
    @Column(name = "extracted_character_count", nullable = false)
    private int extractedCharacterCount;
    @Column(name = "failure_message", length = 240)
    private String failureMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "indexed_at")
    private Instant indexedAt;

    protected KnowledgeDocument() {}

    public static KnowledgeDocument processing(UUID ownerId, UUID logicalId, int version, String fileName,
            String format, String contentType, String storageKey, long size, String sha256, Instant now) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.id = UUID.randomUUID();
        document.ownerId = ownerId;
        document.logicalId = logicalId;
        document.version = version;
        document.fileName = fileName;
        document.format = format;
        document.contentType = contentType;
        document.storageKey = storageKey;
        document.size = size;
        document.sha256 = sha256;
        document.indexState = KnowledgeIndexState.PROCESSING;
        document.createdAt = now;
        return document;
    }

    public void indexed(int characters, Instant now) {
        indexState = KnowledgeIndexState.INDEXED;
        currentVersion = true;
        extractedCharacterCount = characters;
        indexedAt = now;
        failureMessage = null;
    }

    public void failed(String message) {
        indexState = KnowledgeIndexState.FAILED;
        currentVersion = false;
        failureMessage = message;
    }

    public void notCurrent() { currentVersion = false; }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getLogicalId() { return logicalId; }
    public int getVersion() { return version; }
    public String getFileName() { return fileName; }
    public String getFormat() { return format; }
    public String getContentType() { return contentType; }
    public String getStorageKey() { return storageKey; }
    public long getSize() { return size; }
    public String getSha256() { return sha256; }
    public KnowledgeIndexState getIndexState() { return indexState; }
    public boolean isCurrentVersion() { return currentVersion; }
    public int getExtractedCharacterCount() { return extractedCharacterCount; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getIndexedAt() { return indexedAt; }
}
