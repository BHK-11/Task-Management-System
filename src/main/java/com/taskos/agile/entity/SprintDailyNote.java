package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sprint_daily_notes", schema = "task_os")
public class SprintDailyNote {
    @Id private UUID id;
    @Column(name = "sprint_id", nullable = false, updatable = false) private UUID sprintId;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(nullable = false, length = 2000) private String progress;
    @Column(name = "next_steps", nullable = false, length = 2000) private String nextSteps;
    @Column(nullable = false, length = 2000) private String blockers;
    @Column(nullable = false, length = 3000) private String notes;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected SprintDailyNote() { }
    public static SprintDailyNote create(UUID sprintId, UUID actorId, String progress, String next,
            String blockers, String notes, Instant now) {
        SprintDailyNote item = new SprintDailyNote(); item.id = UUID.randomUUID(); item.sprintId = sprintId;
        item.actorId = actorId; item.progress = clean(progress); item.nextSteps = clean(next);
        item.blockers = clean(blockers); item.notes = clean(notes); item.createdAt = now; return item;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    public UUID getId() { return id; }
    public UUID getSprintId() { return sprintId; }
    public String getProgress() { return progress; }
    public String getNextSteps() { return nextSteps; }
    public String getBlockers() { return blockers; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}
