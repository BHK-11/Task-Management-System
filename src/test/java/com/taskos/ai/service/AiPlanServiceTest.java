package com.taskos.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.taskos.ai.dto.AiActionType;
import com.taskos.ai.dto.AiPlannerResponse;
import com.taskos.ai.dto.AiPlannerPlanDraft;
import com.taskos.ai.dto.AiPlannerStepDraft;
import com.taskos.ai.dto.AiWorkPlannerPlanDraft;
import com.taskos.ai.dto.AiWorkPlannerResponse;
import com.taskos.ai.dto.AiWorkPlannerStepDraft;
import com.taskos.task.entity.TaskPriority;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AiPlanServiceTest {
    @Test
    void topologicalSortRunsPrerequisitesBeforeDependentTasks() {
        var steps = List.of(
                step("Follow-up", List.of(1)),
                step("Prepare", List.of()),
                step("Review", List.of(1)));

        assertEquals(List.of(1, 0, 2), AiPlanService.topologicalOrderDrafts(steps));
    }

    @Test
    void plannerRejectsCyclicOrOutOfRangeDependencies() {
        var cycle = List.of(step("A", List.of(1)), step("B", List.of(0)));
        var invalid = List.of(step("A", List.of(4)));

        assertThrows(AiCopilotException.class, () -> AiPlanService.topologicalOrderDrafts(cycle));
        assertThrows(AiCopilotException.class, () -> AiPlanService.topologicalOrderDrafts(invalid));
    }

    @Test
    void plannerRejectsUnregisteredActionTypesAtJavaBoundary() {
        var draft = new AiPlannerPlanDraft("summary", "en", List.of(),
                List.of(new AiPlannerStepDraft(null, "run shell", "", TaskPriority.MEDIUM, null, List.of())));
        assertThrows(AiCopilotException.class,
                () -> AiPlanService.validate(new AiPlannerResponse(draft, "test-model", 1, 1)));
    }

    @Test
    void plannerRejectsMoreThanTwelveSteps() {
        var steps = IntStream.range(0, 13).mapToObj(index -> step("Step " + index, List.of())).toList();
        var draft = new AiPlannerPlanDraft("summary", "en", List.of(), steps);

        assertThrows(AiCopilotException.class,
                () -> AiPlanService.validate(new AiPlannerResponse(draft, "test-model", 1, 1)));
    }

    @Test
    void workPlannerAcceptsIndependentMixedActionsAndRejectsMissingDeliverableContent() {
        var valid = new AiWorkPlannerResponse(new AiWorkPlannerPlanDraft("Mixed plan", "hinglish", List.of(), List.of(
                new AiWorkPlannerStepDraft(AiActionType.CREATE_TASK, "Revise Java", "", TaskPriority.MEDIUM, null, List.of(), ""),
                new AiWorkPlannerStepDraft(AiActionType.GENERATE_DOCX, "Guide", "", null, null, List.of(), "## Guide\nContent"))),
                "test-model", 2, 3);
        AiPlanService.validateWork(valid);
        var missingContent = new AiWorkPlannerResponse(new AiWorkPlannerPlanDraft("Mixed plan", "en", List.of(), List.of(
                new AiWorkPlannerStepDraft(AiActionType.GENERATE_PDF, "Guide", "", null, null, List.of(), ""))),
                "test-model", 2, 3);
        assertThrows(AiCopilotException.class, () -> AiPlanService.validateWork(missingContent));
    }

    @Test
    void workPlannerRejectsDependencyCyclesAtJavaBoundary() {
        var cyclic = new AiWorkPlannerResponse(new AiWorkPlannerPlanDraft("Cycle", "en", List.of(), List.of(
                new AiWorkPlannerStepDraft(AiActionType.CREATE_TASK, "A", "", TaskPriority.MEDIUM, null, List.of(1), ""),
                new AiWorkPlannerStepDraft(AiActionType.CREATE_TASK, "B", "", TaskPriority.MEDIUM, null, List.of(0), ""))),
                "test-model", 2, 3);
        assertThrows(AiCopilotException.class, () -> AiPlanService.validateWork(cyclic));
    }

    private AiPlannerStepDraft step(String title, List<Integer> dependencies) {
        return new AiPlannerStepDraft(AiActionType.CREATE_TASK, title, "", TaskPriority.MEDIUM, null, dependencies);
    }
}
