package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "acceptance_criteria", schema = "task_os")
public class AcceptanceCriterion {
    @Id private UUID id;
    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(nullable = false, length = 500) private String criterion;
    @Column(nullable = false) private boolean satisfied;
    @Column(nullable = false) private int position;
    @Version private Long version;

    protected AcceptanceCriterion() { }
    public static AcceptanceCriterion create(UUID taskId, String criterion, int position) {
        AcceptanceCriterion item = new AcceptanceCriterion(); item.id = UUID.randomUUID(); item.taskId = taskId;
        item.criterion = criterion.trim(); item.position = position; return item;
    }
    public void update(String criterion) { this.criterion = criterion.trim(); }
    public void setSatisfied(boolean satisfied) { this.satisfied = satisfied; }
    public void moveTo(int position) { this.position = position; }
    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getCriterion() { return criterion; }
    public boolean isSatisfied() { return satisfied; }
    public int getPosition() { return position; }
}
