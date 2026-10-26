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
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_plans", schema = "task_os")
public class DailyPlan {
    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @Column(name = "planning_date", nullable = false, updatable = false)
    private LocalDate planningDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_mode", nullable = false, length = 16)
    private DailyPlanMode mode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frog_task_id")
    private Task frogTask;

    @Version private Long version;

    protected DailyPlan() { }

    public static DailyPlan create(UserAccount owner, LocalDate date, DailyPlanMode mode) {
        DailyPlan plan = new DailyPlan();
        plan.id = UUID.randomUUID();
        plan.owner = owner;
        plan.planningDate = date;
        plan.mode = mode;
        return plan;
    }

    public void changeMode(DailyPlanMode mode) { this.mode = mode; }
    public void selectFrog(Task task) { this.frogTask = task; }
    public UUID getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public LocalDate getPlanningDate() { return planningDate; }
    public DailyPlanMode getMode() { return mode; }
    public Task getFrogTask() { return frogTask; }
}
