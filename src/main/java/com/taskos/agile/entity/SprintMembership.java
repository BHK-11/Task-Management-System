package com.taskos.agile.entity;

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
@Table(name = "sprint_memberships", schema = "task_os")
public class SprintMembership {
    @Id private UUID id;
    @Column(name = "sprint_id", nullable = false, updatable = false) private UUID sprintId;
    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SprintMembershipStatus status;
    @Column(nullable = false) private int position;
    @Column(name = "added_at", nullable = false) private Instant addedAt;
    @Column(name = "removed_at") private Instant removedAt;
    @Version private Long version;

    protected SprintMembership() { }
    public static SprintMembership planned(UUID sprintId, UUID taskId, int position, Instant now) {
        SprintMembership item = new SprintMembership(); item.id = UUID.randomUUID(); item.sprintId = sprintId;
        item.taskId = taskId; item.status = SprintMembershipStatus.PLANNED; item.position = position; item.addedAt = now; return item;
    }
    public void activate() { status = SprintMembershipStatus.ACTIVE; }
    public void reopen(SprintMembershipStatus status, int position, Instant now) {
        this.status = status; this.position = position; this.addedAt = now; this.removedAt = null;
    }
    public void close(SprintMembershipStatus status, Instant now) { this.status = status; removedAt = now; }
    public void moveTo(int position) { this.position = position; }
    public UUID getId() { return id; }
    public UUID getSprintId() { return sprintId; }
    public UUID getTaskId() { return taskId; }
    public SprintMembershipStatus getStatus() { return status; }
    public int getPosition() { return position; }
    public Instant getAddedAt() { return addedAt; }
}
