package com.taskos.ai.entity;

import com.taskos.user.entity.UserAccount;
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
@Table(name = "ai_artifacts", schema = "task_os")
public class AiArtifact {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private AiPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false, updatable = false)
    private AiPlanStep step;

    @Column(name = "file_name", nullable = false, length = 160)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "storage_key", nullable = false, length = 80, unique = true)
    private String storageKey;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiArtifact() {}

    public static AiArtifact create(UserAccount owner, AiPlan plan, AiPlanStep step, String fileName,
            String contentType, long size, String sha256, Instant now) {
        AiArtifact artifact = new AiArtifact();
        artifact.id = UUID.randomUUID();
        artifact.owner = owner;
        artifact.plan = plan;
        artifact.step = step;
        artifact.fileName = fileName;
        artifact.contentType = contentType;
        artifact.storageKey = artifact.id.toString();
        artifact.size = size;
        artifact.sha256 = sha256;
        artifact.createdAt = now;
        return artifact;
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public String getStorageKey() { return storageKey; }
    public long getSize() { return size; }
    public String getSha256() { return sha256; }
    public Instant getCreatedAt() { return createdAt; }
}
