package com.taskos.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_memories", schema = "task_os")
public class AiMemory {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AiMemoryCategory category;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "source_type", nullable = false, length = 24, updatable = false)
    private String sourceType;

    @Column(name = "source_note", length = 240)
    private String sourceNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiMemoryStatus status;

    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    protected AiMemory() {}

    public static AiMemory confirmed(UUID ownerId, AiMemoryCategory category, String content,
            String sourceNote, UUID supersedesId, Instant now) {
        AiMemory memory = new AiMemory();
        memory.id = UUID.randomUUID();
        memory.ownerId = ownerId;
        memory.category = category;
        memory.content = content;
        memory.sourceType = "USER_CONFIRMED";
        memory.sourceNote = sourceNote;
        memory.status = AiMemoryStatus.ACTIVE;
        memory.supersedesId = supersedesId;
        memory.createdAt = now;
        memory.updatedAt = now;
        memory.confirmedAt = now;
        return memory;
    }

    public void supersede(Instant now) {
        status = AiMemoryStatus.SUPERSEDED;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public AiMemoryCategory getCategory() { return category; }
    public String getContent() { return content; }
    public String getSourceType() { return sourceType; }
    public String getSourceNote() { return sourceNote; }
    public AiMemoryStatus getStatus() { return status; }
    public UUID getSupersedesId() { return supersedesId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
