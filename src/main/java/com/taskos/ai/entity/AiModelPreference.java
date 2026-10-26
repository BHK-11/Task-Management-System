package com.taskos.ai.entity;

import com.taskos.ai.dto.AiModelMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_model_preferences", schema = "task_os")
public class AiModelPreference {
    @Id
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiModelMode mode;

    @Column(name = "provider_id", length = 32)
    private String providerId;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(name = "allow_cloud_processing", nullable = false)
    private boolean allowCloudProcessing;

    @Column(name = "allow_fallback", nullable = false)
    private boolean allowFallback;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiModelPreference() {}

    public static AiModelPreference defaults(UUID ownerId, Instant now) {
        AiModelPreference preference = new AiModelPreference();
        preference.ownerId = ownerId;
        preference.mode = AiModelMode.AUTO;
        preference.allowCloudProcessing = true;
        preference.allowFallback = false;
        preference.updatedAt = now;
        return preference;
    }

    public void update(AiModelMode mode, String providerId, String modelId,
            boolean allowCloudProcessing, boolean allowFallback, Instant now) {
        this.mode = mode;
        this.providerId = providerId;
        this.modelId = modelId;
        this.allowCloudProcessing = allowCloudProcessing;
        this.allowFallback = allowFallback;
        this.updatedAt = now;
    }

    public UUID getOwnerId() { return ownerId; }
    public AiModelMode getMode() { return mode; }
    public String getProviderId() { return providerId; }
    public String getModelId() { return modelId; }
    public boolean isAllowCloudProcessing() { return allowCloudProcessing; }
    public boolean isAllowFallback() { return allowFallback; }
    public Instant getUpdatedAt() { return updatedAt; }
}
