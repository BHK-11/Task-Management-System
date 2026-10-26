package com.taskos.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_memory_preferences", schema = "task_os")
public class AiMemoryPreference {
    @Id
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "consent_version", nullable = false)
    private int consentVersion;

    @Column(name = "consented_at")
    private Instant consentedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiMemoryPreference() {}

    public static AiMemoryPreference defaults(UUID ownerId, Instant now) {
        AiMemoryPreference preference = new AiMemoryPreference();
        preference.ownerId = ownerId;
        preference.enabled = false;
        preference.consentVersion = 1;
        preference.updatedAt = now;
        return preference;
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
        if (enabled) {
            this.consentedAt = now;
            this.revokedAt = null;
        } else if (this.consentedAt != null) {
            this.revokedAt = now;
        }
    }

    public UUID getOwnerId() { return ownerId; }
    public boolean isEnabled() { return enabled; }
    public int getConsentVersion() { return consentVersion; }
    public Instant getConsentedAt() { return consentedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
