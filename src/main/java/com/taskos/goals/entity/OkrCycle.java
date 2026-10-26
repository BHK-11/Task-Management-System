package com.taskos.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "okr_cycles", schema = "task_os")
public class OkrCycle {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected OkrCycle() { }
    public static OkrCycle create(UUID ownerId, String name, LocalDate start, LocalDate end, Instant now) {
        var cycle = new OkrCycle(); cycle.id = UUID.randomUUID(); cycle.ownerId = ownerId; cycle.name = name.strip();
        cycle.startDate = start; cycle.endDate = end; cycle.status = "ACTIVE"; cycle.createdAt = now; return cycle;
    }
    public UUID getId() { return id; } public UUID getOwnerId() { return ownerId; } public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; } public LocalDate getEndDate() { return endDate; } public String getStatus() { return status; }
}
