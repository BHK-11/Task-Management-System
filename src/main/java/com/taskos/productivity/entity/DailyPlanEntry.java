package com.taskos.productivity.entity;

import com.taskos.task.entity.Task;
import com.taskos.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "daily_plan_entries", schema = "task_os")
public class DailyPlanEntry {
    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private DailyPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlanBucket bucket;

    @Column(nullable = false)
    private int position;

    protected DailyPlanEntry() { }

    public static DailyPlanEntry create(DailyPlan plan, UserAccount owner, Task task, PlanBucket bucket, int position) {
        DailyPlanEntry entry = new DailyPlanEntry();
        entry.id = UUID.randomUUID();
        entry.plan = plan;
        entry.owner = owner;
        entry.task = task;
        entry.bucket = bucket;
        entry.position = position;
        return entry;
    }

    public void move(PlanBucket bucket, int position) { this.bucket = bucket; this.position = position; }
    public UUID getId() { return id; }
    public DailyPlan getPlan() { return plan; }
    public UserAccount getOwner() { return owner; }
    public Task getTask() { return task; }
    public PlanBucket getBucket() { return bucket; }
    public int getPosition() { return position; }
}
