package com.taskos.ai.entity;

import com.taskos.ai.dto.AiActionType;
import com.taskos.ai.dto.AiPlanStepStatus;
import com.taskos.ai.dto.AiPlannerStepDraft;
import com.taskos.task.entity.TaskPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_plan_steps", schema = "task_os")
public class AiPlanStep {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private AiPlan plan;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 24)
    private AiActionType actionType;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 6000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, length = 255)
    private String dependencies;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AiPlanStepStatus status;

    @Column(name = "result_task_id")
    private UUID resultTaskId;

    @Column(name = "result_summary", nullable = false, length = 500)
    private String resultSummary;

    @Column(name = "content", nullable = false, length = 12000)
    private String content;

    @Column(name = "result_artifact_id")
    private UUID resultArtifactId;

    protected AiPlanStep() {}

    static AiPlanStep create(AiPlan plan, int index, AiPlannerStepDraft draft) {
        AiPlanStep step = new AiPlanStep();
        step.id = UUID.randomUUID();
        step.plan = plan;
        step.stepIndex = index;
        step.actionType = draft.actionType();
        step.title = draft.title().strip();
        step.description = draft.description() == null ? "" : draft.description().strip();
        step.priority = draft.priority() == null ? TaskPriority.MEDIUM : draft.priority();
        step.dueDate = draft.dueDate();
        step.dependencies = draft.dependencies().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        step.content = "";
        step.status = draft.actionType() == AiActionType.UNSUPPORTED ? AiPlanStepStatus.UNSUPPORTED : AiPlanStepStatus.PENDING;
        step.resultSummary = draft.actionType() == AiActionType.UNSUPPORTED ? "This action is not available in the copilot yet." : "";
        return step;
    }

    static AiPlanStep createWork(AiPlan plan, int index, com.taskos.ai.dto.AiWorkPlannerStepDraft draft) {
        AiPlanStep step = new AiPlanStep();
        step.id = UUID.randomUUID();
        step.plan = plan;
        step.stepIndex = index;
        step.actionType = draft.actionType();
        step.title = draft.title().strip();
        step.description = draft.description() == null ? "" : draft.description().strip();
        step.priority = draft.priority() == null ? TaskPriority.MEDIUM : draft.priority();
        step.dueDate = draft.dueDate();
        step.dependencies = draft.dependencies().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        step.content = draft.content() == null ? "" : draft.content().strip();
        step.status = draft.actionType() == AiActionType.UNSUPPORTED ? AiPlanStepStatus.UNSUPPORTED : AiPlanStepStatus.PENDING;
        step.resultSummary = draft.actionType() == AiActionType.UNSUPPORTED ? "This action is not available in the work manager." : "";
        return step;
    }

    public void update(String title, String description, TaskPriority priority, LocalDate dueDate) {
        this.title = title.strip();
        this.description = description == null ? "" : description.strip();
        this.priority = priority;
        this.dueDate = dueDate;
    }

    public void updateWork(String title, String description, String content, TaskPriority priority, LocalDate dueDate) {
        update(title, description, priority, dueDate);
        this.content = content == null ? "" : content.strip();
    }

    public void complete(UUID taskId) {
        resultTaskId = taskId;
        resultSummary = "Task created and verified through TASK OS.";
        status = AiPlanStepStatus.COMPLETED;
    }

    public void start() { status = AiPlanStepStatus.RUNNING; resultSummary = "Working on this item."; }

    public void completeArtifact(UUID artifactId, String summary) {
        resultArtifactId = artifactId;
        resultSummary = summary;
        status = AiPlanStepStatus.COMPLETED;
    }

    public void fail(String summary) { status = AiPlanStepStatus.FAILED; resultSummary = summary; }

    public void block() {
        status = AiPlanStepStatus.BLOCKED;
        resultSummary = "A prerequisite was unsupported or could not be completed.";
    }

    public UUID getId() { return id; }
    public int getStepIndex() { return stepIndex; }
    public AiActionType getActionType() { return actionType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskPriority getPriority() { return priority; }
    public LocalDate getDueDate() { return dueDate; }
    public AiPlanStepStatus getStatus() { return status; }
    public UUID getResultTaskId() { return resultTaskId; }
    public String getResultSummary() { return resultSummary; }
    public UUID getResultArtifactId() { return resultArtifactId; }
    public String getContent() { return content; }
    public String getDependenciesLabel() {
        return getDependencies().stream().map(index -> String.valueOf(index + 1))
                .collect(java.util.stream.Collectors.joining(", "));
    }
    public List<Integer> getDependencies() {
        return dependencies.isBlank() ? List.of() : Arrays.stream(dependencies.split(",")).map(Integer::parseInt).toList();
    }
}
