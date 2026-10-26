package com.taskos.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reviews", schema = "task_os")
public class Review {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Enumerated(EnumType.STRING) @Column(name = "review_type", nullable = false, length = 12) private ReviewType type;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(nullable = false, length = 12) private String status;
    @Column(nullable = false, length = 3000) private String reflection;
    @Column(nullable = false, length = 2000) private String wins;
    @Column(nullable = false, length = 2000) private String challenges;
    @Column(name = "next_steps", nullable = false, length = 2000) private String nextSteps;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;
    protected Review() { }
    public static Review create(UUID ownerId, ReviewType type, LocalDate start, LocalDate end, Instant now) {
        var review = new Review(); review.id = UUID.randomUUID(); review.ownerId = ownerId; review.type = type;
        review.periodStart = start; review.periodEnd = end; review.status = "DRAFT"; review.reflection = "";
        review.wins = ""; review.challenges = ""; review.nextSteps = ""; review.createdAt = now; review.updatedAt = now; return review;
    }
    public void save(String reflection, String wins, String challenges, String nextSteps, boolean complete, Instant now) {
        this.reflection = safe(reflection); this.wins = safe(wins); this.challenges = safe(challenges); this.nextSteps = safe(nextSteps);
        this.status = complete ? "COMPLETED" : "DRAFT"; this.completedAt = complete ? now : null; this.updatedAt = now;
    }
    private String safe(String value) { return value == null ? "" : value.strip(); }
    public UUID getId() { return id; } public UUID getOwnerId() { return ownerId; } public ReviewType getType() { return type; }
    public LocalDate getPeriodStart() { return periodStart; } public LocalDate getPeriodEnd() { return periodEnd; }
    public String getStatus() { return status; } public String getReflection() { return reflection; } public String getWins() { return wins; }
    public String getChallenges() { return challenges; } public String getNextSteps() { return nextSteps; } public Instant getCompletedAt() { return completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
