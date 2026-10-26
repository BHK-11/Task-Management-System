package com.taskos.auth.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_events", schema = "task_os", indexes = {
        @Index(name = "ix_auth_events_occurred_at", columnList = "occurred_at"),
        @Index(name = "ix_auth_events_user_time", columnList = "user_id, occurred_at")
})
public class AuthEvent {
    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private AuthEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuthEvent() {
    }

    public AuthEvent(UUID userId, AuthEventType eventType, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public AuthEventType getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
}
