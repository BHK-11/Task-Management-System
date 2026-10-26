package com.taskos.ai.service;

import com.taskos.ai.dto.AiPlanStatus;
import com.taskos.ai.dto.AiPlanStepStatus;
import com.taskos.ai.entity.AiArtifact;
import com.taskos.ai.entity.AiPlan;
import com.taskos.ai.entity.AiPlanStep;
import com.taskos.ai.repository.AiPlanRepository;
import com.taskos.ai.repository.AiArtifactRepository;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.service.TaskService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiPlanExecutionPersistenceService {
    private final AiPlanRepository plans;
    private final TaskService tasks;
    private final AiArtifactStorageService artifacts;
    private final AiArtifactRepository artifactRepository;
    private final Clock clock;

    public AiPlanExecutionPersistenceService(AiPlanRepository plans, TaskService tasks,
            AiArtifactStorageService artifacts, AiArtifactRepository artifactRepository, Clock clock) {
        this.plans = plans; this.tasks = tasks; this.artifacts = artifacts; this.artifactRepository = artifactRepository; this.clock = clock;
    }

    @Transactional
    public Optional<UUID> claimNext() {
        var now = clock.instant();
        List<AiPlan> due = plans.lockNextRunnable(now, PageRequest.of(0, 1));
        if (due.isEmpty()) return Optional.empty();
        AiPlan plan = due.getFirst();
        plan.claim(now.plus(Duration.ofMinutes(2)));
        return Optional.of(plan.getId());
    }

    @Transactional(readOnly = true)
    public PlanWork load(UUID planId) {
        AiPlan plan = plans.findById(planId).orElseThrow(() -> new AiCopilotException("The approved plan is unavailable."));
        return new PlanWork(plan.getId(), plan.getOwnerId(), plan.getSteps().stream().map(step -> new StepWork(
                step.getStepIndex(), step.getActionType(), step.getTitle(), step.getDescription(), step.getContent(),
                step.getPriority(), step.getDueDate(), step.getDependencies(), step.getStatus())).toList());
    }

    @Transactional
    public void startStep(UUID planId, int index) {
        AiPlan plan = locked(planId);
        AiPlanStep step = step(plan, index);
        if (step.getStatus() == AiPlanStepStatus.PENDING) step.start();
        plan.renewLease(clock.instant().plus(Duration.ofMinutes(2)));
    }

    @Transactional
    public void createTask(UUID planId, int index) {
        AiPlan plan = locked(planId);
        AiPlanStep step = step(plan, index);
        if (step.getStatus() == AiPlanStepStatus.COMPLETED) return;
        TaskForm form = new TaskForm();
        form.setTitle(step.getTitle()); form.setDescription(step.getDescription());
        form.setStatus(TaskStatus.TODO); form.setPriority(step.getPriority()); form.setDueDate(step.getDueDate());
        step.complete(tasks.create(plan.getOwnerId(), form));
        release(plan);
    }

    @Transactional
    public AiArtifact saveArtifact(UUID planId, int index, String fileName, String contentType, byte[] bytes) {
        AiPlan plan = locked(planId);
        AiPlanStep step = step(plan, index);
        if (step.getStatus() == AiPlanStepStatus.COMPLETED)
            return artifactRepository.findByIdAndOwner_Id(step.getResultArtifactId(), plan.getOwnerId())
                    .orElseThrow(() -> new AiCopilotException("The completed deliverable is unavailable."));
        AiArtifact artifact = artifacts.store(plan.getOwnerId(), plan, step, fileName, contentType, bytes);
        step.completeArtifact(artifact.getId(), "File generated, format-checked, and stored privately. Download integrity is checked when fetched.");
        release(plan);
        return artifact;
    }

    @Transactional
    public void failStep(UUID planId, int index) {
        AiPlan plan = locked(planId);
        AiPlanStep step = step(plan, index);
        if (step.getStatus() != AiPlanStepStatus.COMPLETED && step.getStatus() != AiPlanStepStatus.UNSUPPORTED)
            step.fail("This item could not be completed. No result was verified.");
        release(plan);
    }

    @Transactional
    public void blockStep(UUID planId, int index) {
        AiPlan plan = locked(planId);
        AiPlanStep step = step(plan, index);
        if (step.getStatus() == AiPlanStepStatus.PENDING || step.getStatus() == AiPlanStepStatus.RUNNING) step.block();
        release(plan);
    }

    @Transactional
    public void finishIfDone(UUID planId) {
        AiPlan plan = locked(planId);
        boolean done = plan.getSteps().stream().allMatch(step -> switch (step.getStatus()) {
            case COMPLETED, FAILED, UNSUPPORTED, BLOCKED -> true;
            case PENDING, RUNNING -> false;
        });
        if (!done) { release(plan); return; }
        boolean allCompleted = plan.getSteps().stream().allMatch(step -> step.getStatus() == AiPlanStepStatus.COMPLETED);
        boolean someCompleted = plan.getSteps().stream().anyMatch(step -> step.getStatus() == AiPlanStepStatus.COMPLETED);
        plan.decide(allCompleted ? AiPlanStatus.COMPLETED : someCompleted ? AiPlanStatus.PARTIALLY_COMPLETED : AiPlanStatus.FAILED,
                clock.instant());
    }

    private AiPlan locked(UUID id) {
        return plans.findLockedById(id)
                .orElseThrow(() -> new AiCopilotException("The approved plan is unavailable."));
    }

    private AiPlanStep step(AiPlan plan, int index) {
        return plan.getSteps().stream().filter(candidate -> candidate.getStepIndex() == index).findFirst()
                .orElseThrow(() -> new AiCopilotException("The approved plan step is unavailable."));
    }

    private void release(AiPlan plan) { plan.requeue(); }

    public record PlanWork(UUID id, UUID ownerId, List<StepWork> steps) {}
    public record StepWork(int index, com.taskos.ai.dto.AiActionType actionType, String title, String description,
            String content, com.taskos.task.entity.TaskPriority priority, java.time.LocalDate dueDate,
            List<Integer> dependencies, AiPlanStepStatus status) {}
}
