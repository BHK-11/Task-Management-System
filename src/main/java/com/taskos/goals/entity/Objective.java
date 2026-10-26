package com.taskos.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "objectives", schema = "task_os")
public class Objective {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "cycle_id", nullable = false, updatable = false) private UUID cycleId;
    @Column(name = "linked_goal_id") private UUID linkedGoalId;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 1200) private String description;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "confidence_note", nullable = false, length = 300) private String confidenceNote;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version private Long version;
    protected Objective() { }
    public static Objective create(UUID ownerId, UUID cycleId, UUID linkedGoalId, String title, String description, Instant now) {
        var o = new Objective(); o.id = UUID.randomUUID(); o.ownerId = ownerId; o.cycleId = cycleId; o.linkedGoalId = linkedGoalId;
        o.title = title.strip(); o.description = description == null ? "" : description.strip(); o.status = "ACTIVE";
        o.confidenceNote = ""; o.createdAt = now; o.updatedAt = now; return o;
    }
    public void updateConfidence(String note, Instant now) { confidenceNote = note == null ? "" : note.strip(); updatedAt = now; }
    public void transition(String next, Instant now) {
        if (!java.util.Set.of("ACTIVE", "COMPLETED", "CANCELLED").contains(next)) throw new IllegalArgumentException("Choose a valid objective status.");
        if (status.equals(next)) return;
        if ("ACTIVE".equals(status) || "ACTIVE".equals(next)) { status = next; completedAt = "COMPLETED".equals(next) ? now : null; updatedAt = now; return; }
        throw new IllegalArgumentException("That objective status change is not available.");
    }
    public UUID getId() { return id; } public UUID getOwnerId() { return ownerId; } public UUID getCycleId() { return cycleId; }
    public UUID getLinkedGoalId() { return linkedGoalId; } public String getTitle() { return title; } public String getDescription() { return description; }
    public String getStatus() { return status; } public String getConfidenceNote() { return confidenceNote; }
}
