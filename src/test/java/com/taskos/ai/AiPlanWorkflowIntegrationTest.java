package com.taskos.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.taskos.ai.dto.AiActionType;
import com.taskos.ai.dto.AiPlanStatus;
import com.taskos.ai.dto.AiPlannerPlanDraft;
import com.taskos.ai.dto.AiPlannerResponse;
import com.taskos.ai.dto.AiPlannerStepDraft;
import com.taskos.ai.service.AiCopilotException;
import com.taskos.ai.service.AiPlanPersistenceService;
import com.taskos.ai.service.AiPlanExecutionService;
import com.taskos.ai.service.AiPlanService;
import com.taskos.auth.service.RegistrationService;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.repository.TaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiPlanWorkflowIntegrationTest {
    @Autowired private RegistrationService registration;
    @Autowired private AiPlanPersistenceService persistence;
    @Autowired private AiPlanService plans;
    @Autowired private AiPlanExecutionService execution;
    @Autowired private TaskRepository tasks;

    @Test
    void approvedPlanCreatesOwnerScopedTasksOnceThroughTaskService() {
        var owner = registration.register("Copilot Owner", "copilot-owner-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var plan = persistence.save(owner.getId(), "Create two revision tasks", response(), Instant.now());

        var queued = plans.approve(owner.getId(), plan.getId());
        assertEquals(AiPlanStatus.QUEUED, queued.getStatus());
        execution.executeNext();
        var progress = plans.detail(owner.getId(), plan.getId());
        assertEquals(1, progress.getSteps().stream().filter(step -> step.getStatus().name().equals("COMPLETED")).count());
        execution.executeNext();
        var completed = plans.detail(owner.getId(), plan.getId());

        assertEquals(AiPlanStatus.COMPLETED, completed.getStatus());
        assertEquals(2, completed.getSteps().size());
        completed.getSteps().forEach(step -> {
            assertNotNull(step.getResultTaskId());
            assertTrue(tasks.findDetailByIdAndOwner(step.getResultTaskId(), owner.getId()).isPresent());
        });
        assertThrows(AiCopilotException.class, () -> plans.approve(owner.getId(), plan.getId()));
    }

    @Test
    void anotherOwnerCannotApproveAPlan() {
        var owner = registration.register("Plan Owner", "copilot-plan-owner-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var other = registration.register("Other Owner", "copilot-other-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var plan = persistence.save(owner.getId(), "Create a study task", response(), Instant.now());

        assertThrows(AiCopilotException.class, () -> plans.approve(other.getId(), plan.getId()));
    }

    private AiPlannerResponse response() {
        return new AiPlannerResponse(new AiPlannerPlanDraft("Create two Java study tasks", "en", List.of(), List.of(
                new AiPlannerStepDraft(AiActionType.CREATE_TASK, "Review polymorphism", "", TaskPriority.HIGH,
                        null, List.of(1)),
                new AiPlannerStepDraft(AiActionType.CREATE_TASK, "Review inheritance", "", TaskPriority.MEDIUM,
                        null, List.of()))), "test-model", 12, 22);
    }
}
