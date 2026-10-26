package com.taskos.scheduling.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.taskos.scheduling.entity.TaskDependencyType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CriticalPathCalculatorTest {
    private final CriticalPathCalculator calculator = new CriticalPathCalculator();
    private final LocalDate origin = LocalDate.of(2026, 1, 5);

    @Test
    void calculatesEarlyLateDatesAndFloatForTwoBranches() {
        UUID a = id("a"), b = id("b"), c = id("c"), d = id("d");
        ScheduleResult result = calculator.calculate(origin,
                List.of(task(a, 5), task(b, 4), task(c, 3), task(d, 2)),
                List.of(edge("ab", a, b, TaskDependencyType.FS, 0), edge("ac", a, c, TaskDependencyType.FS, 0),
                        edge("bd", b, d, TaskDependencyType.FS, 0), edge("cd", c, d, TaskDependencyType.FS, 0)));

        assertEquals(ScheduleResult.Status.READY, result.status());
        assertEquals(11, result.projectDurationDays());
        assertEquals(origin.plusDays(10), result.scheduledProjectFinish());
        assertEquals(5, result.taskResults().get(b).earliestStartOffset());
        assertEquals(9, result.taskResults().get(d).earliestStartOffset());
        assertEquals(1, result.taskResults().get(c).totalFloatDays());
        assertFalse(result.taskResults().get(c).critical());
        assertTrue(result.taskResults().get(b).critical());
        assertEquals(List.of(id("ab"), id("bd")), result.criticalDependencyIds());
    }

    @Test
    void calculatesEveryPrecedenceEquationAndPositiveLag() {
        UUID a = id("mix-a"), ss = id("mix-ss"), ff = id("mix-ff"), sf = id("mix-sf"), fs = id("mix-fs");
        ScheduleResult result = calculator.calculate(origin,
                List.of(task(a, 4), task(ss, 2), task(ff, 2), task(sf, 2), task(fs, 1)),
                List.of(edge("ss", a, ss, TaskDependencyType.SS, 1), edge("ff", a, ff, TaskDependencyType.FF, 0),
                        edge("sf", a, sf, TaskDependencyType.SF, 3), edge("fs", a, fs, TaskDependencyType.FS, 2)));

        assertEquals(1, result.taskResults().get(ss).earliestStartOffset());
        assertEquals(2, result.taskResults().get(ff).earliestStartOffset());
        assertEquals(1, result.taskResults().get(sf).earliestStartOffset());
        assertEquals(6, result.taskResults().get(fs).earliestStartOffset());
    }

    @Test
    void usesEveryPredecessorAndKeepsIndependentRoots() {
        UUID a = id("multi-a"), b = id("multi-b"), c = id("multi-c"), free = id("multi-free");
        ScheduleResult result = calculator.calculate(origin,
                List.of(task(a, 3), new ScheduleTaskInput(b, "b", 5, origin.plusDays(4)), task(c, 2),
                        new ScheduleTaskInput(free, "free", 1, origin.plusDays(4))),
                List.of(edge("ac", a, c, TaskDependencyType.FS, 0), edge("bc", b, c, TaskDependencyType.SS, 1)));

        assertEquals(5, result.taskResults().get(c).earliestStartOffset());
        assertEquals(4, result.taskResults().get(free).earliestStartOffset());
        assertEquals(4, result.topologicalOrder().size());
    }

    @Test
    void preservesTwoCriticalBranchesAndZeroDurationMilestone() {
        UUID a = id("branch-a"), b = id("branch-b"), c = id("branch-c"), d = id("branch-d"), milestone = id("event");
        ScheduleResult branches = calculator.calculate(origin, List.of(task(a, 5), task(b, 4), task(c, 6), task(d, 3)),
                List.of(edge("ab", a, b, TaskDependencyType.FS, 0), edge("cd", c, d, TaskDependencyType.FS, 0)));
        assertEquals(4, branches.criticalTaskIds().size());

        ScheduleResult event = calculator.calculate(origin,
                List.of(task(a, 4), new ScheduleTaskInput(milestone, "Approval", 0, null), task(b, 1)),
                List.of(edge("am", a, milestone, TaskDependencyType.FS, 0), edge("mb", milestone, b, TaskDependencyType.FS, 0)));
        assertEquals(origin.plusDays(4), event.taskResults().get(milestone).earliestStart());
        assertEquals(event.taskResults().get(milestone).earliestStart(), event.taskResults().get(milestone).earliestFinish());
    }

    @Test
    void rejectsCyclesSelfLinksDuplicatesAndNegativeLag() {
        UUID a = id("cycle-a"), b = id("cycle-b"), c = id("cycle-c");
        List<ScheduleTaskInput> nodes = List.of(task(a, 1), task(b, 1), task(c, 1));
        assertThrows(DependencyCycleException.class, () -> calculator.calculate(origin, nodes,
                List.of(edge("ab", a, b, TaskDependencyType.FS, 0), edge("bc", b, c, TaskDependencyType.FS, 0),
                        edge("ca", c, a, TaskDependencyType.FS, 0))));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(origin, nodes,
                List.of(edge("aa", a, a, TaskDependencyType.FS, 0))));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(origin, nodes,
                List.of(edge("ab1", a, b, TaskDependencyType.FS, 0), edge("ab2", a, b, TaskDependencyType.SS, 0))));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(origin, nodes,
                List.of(edge("ab-neg", a, b, TaskDependencyType.FS, -1))));
    }

    @Test
    void refusesToInventDatesWhenOriginOrEstimateIsMissing() {
        UUID a = id("missing-a"), b = id("missing-b");
        ScheduleResult noEstimate = calculator.calculate(origin, List.of(task(a, 1), new ScheduleTaskInput(b, "B", null, null)), List.of());
        assertEquals(ScheduleResult.Status.INCOMPLETE, noEstimate.status());
        assertTrue(noEstimate.taskResults().isEmpty());
        assertTrue(noEstimate.missingInputs().stream().anyMatch(value -> value.contains("Duration required")));

        ScheduleResult noOrigin = calculator.calculate(null, List.of(task(a, 1)), List.of());
        assertEquals(ScheduleResult.Status.INCOMPLETE, noOrigin.status());
        assertTrue(noOrigin.taskResults().isEmpty());
    }

    private static ScheduleTaskInput task(UUID id, int days) { return new ScheduleTaskInput(id, id.toString(), days, null); }
    private static ScheduleDependencyInput edge(String key, UUID from, UUID to, TaskDependencyType type, int lag) {
        return new ScheduleDependencyInput(id(key), from, to, type, lag);
    }
    private static UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
}
