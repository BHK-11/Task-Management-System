package com.taskos.scheduling.service;

import com.taskos.scheduling.entity.TaskDependencyType;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class CriticalPathCalculator {
    public ScheduleResult calculate(LocalDate origin, List<ScheduleTaskInput> tasks,
            List<ScheduleDependencyInput> dependencies) {
        if (tasks == null || dependencies == null) throw new IllegalArgumentException("Schedule inputs are required.");
        Map<UUID, ScheduleTaskInput> byId = new HashMap<>();
        for (ScheduleTaskInput task : tasks) {
            if (task.id() == null || byId.putIfAbsent(task.id(), task) != null)
                throw new IllegalArgumentException("Schedule task identifiers must be present and unique.");
            if (task.durationDays() != null && (task.durationDays() < 0 || task.durationDays() > 36500))
                throw new IllegalArgumentException("Schedule duration must be between 0 and 36500 calendar days.");
        }
        validateDependencies(byId.keySet(), dependencies);
        List<UUID> order = topologicalOrder(byId.keySet(), dependencies);

        List<String> missing = new ArrayList<>();
        if (origin == null) missing.add("Set a project start date to establish the schedule origin.");
        for (ScheduleTaskInput task : tasks) {
            if (task.durationDays() == null) missing.add("Duration required before critical path calculation: " + task.title());
            if (origin != null && task.earliestStartDate() != null && task.earliestStartDate().isBefore(origin))
                missing.add("Planned start is before the project start date: " + task.title());
        }
        if (!missing.isEmpty())
            return new ScheduleResult(ScheduleResult.Status.INCOMPLETE, origin, null, 0, Map.of(), List.of(),
                    List.of(), List.of(), missing);

        Map<UUID, List<ScheduleDependencyInput>> outgoing = new HashMap<>();
        Map<UUID, List<ScheduleDependencyInput>> incoming = new HashMap<>();
        for (ScheduleDependencyInput dependency : dependencies) {
            outgoing.computeIfAbsent(dependency.predecessorId(), ignored -> new ArrayList<>()).add(dependency);
            incoming.computeIfAbsent(dependency.successorId(), ignored -> new ArrayList<>()).add(dependency);
        }
        Map<UUID, Long> early = new HashMap<>(), finish = new HashMap<>();
        for (UUID id : order) {
            ScheduleTaskInput task = byId.get(id);
            long start = task.earliestStartDate() == null ? 0 : ChronoUnit.DAYS.between(origin, task.earliestStartDate());
            if (start < 0) throw new IllegalArgumentException("A planned task start cannot be before the project start date: " + task.title());
            for (ScheduleDependencyInput edge : incoming.getOrDefault(id, List.of()))
                start = Math.max(start, Math.addExact(early.get(edge.predecessorId()), weight(edge, byId)));
            early.put(id, start);
            finish.put(id, Math.addExact(start, task.durationDays()));
        }

        long projectFinish = finish.values().stream().mapToLong(Long::longValue).max().orElse(0);
        Map<UUID, Long> late = new HashMap<>(), lateFinish = new HashMap<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            UUID id = order.get(i);
            long duration = byId.get(id).durationDays();
            long latest = projectFinish - duration;
            for (ScheduleDependencyInput edge : outgoing.getOrDefault(id, List.of()))
                latest = Math.min(latest, Math.subtractExact(late.get(edge.successorId()), weight(edge, byId)));
            late.put(id, latest);
            lateFinish.put(id, Math.addExact(latest, duration));
        }

        Map<UUID, TaskScheduleResult> results = new LinkedHashMap<>();
        List<UUID> criticalTasks = new ArrayList<>(), criticalDependencies = new ArrayList<>();
        for (UUID id : order) {
            long slack = late.get(id) - early.get(id);
            boolean critical = slack == 0;
            if (critical) criticalTasks.add(id);
            long duration = byId.get(id).durationDays();
            results.put(id, new TaskScheduleResult(id, early.get(id), finish.get(id), late.get(id), lateFinish.get(id),
                    slack, critical, startDate(origin, early.get(id)), finishDate(origin, early.get(id), duration),
                    startDate(origin, late.get(id)), finishDate(origin, late.get(id), duration)));
        }
        for (ScheduleDependencyInput edge : dependencies)
            if (criticalTasks.contains(edge.predecessorId()) && criticalTasks.contains(edge.successorId())
                    && early.get(edge.successorId()) == early.get(edge.predecessorId()) + weight(edge, byId))
                criticalDependencies.add(edge.id());

        return new ScheduleResult(ScheduleResult.Status.READY, origin,
                projectFinish == 0 ? origin : finishDate(origin, 0, projectFinish), projectFinish,
                results, order, criticalTasks, criticalDependencies, List.of());
    }

    public List<UUID> topologicalOrder(Set<UUID> taskIds, List<ScheduleDependencyInput> dependencies) {
        validateDependencies(taskIds, dependencies);
        Map<UUID, Integer> indegree = new HashMap<>();
        Map<UUID, List<UUID>> outgoing = new HashMap<>();
        taskIds.forEach(id -> indegree.put(id, 0));
        for (ScheduleDependencyInput edge : dependencies) {
            indegree.compute(edge.successorId(), (ignored, count) -> count + 1);
            outgoing.computeIfAbsent(edge.predecessorId(), ignored -> new ArrayList<>()).add(edge.successorId());
        }
        Comparator<UUID> byId = Comparator.comparing(UUID::toString);
        PriorityQueue<UUID> ready = new PriorityQueue<>(byId);
        indegree.forEach((id, count) -> { if (count == 0) ready.add(id); });
        List<UUID> order = new ArrayList<>(taskIds.size());
        while (!ready.isEmpty()) {
            UUID current = ready.remove(); order.add(current);
            for (UUID successor : outgoing.getOrDefault(current, List.of()))
                if (indegree.compute(successor, (ignored, count) -> count - 1) == 0) ready.add(successor);
        }
        if (order.size() != taskIds.size()) {
            Set<UUID> cyclic = new HashSet<>(taskIds); cyclic.removeAll(order);
            throw new DependencyCycleException(cyclic.stream().sorted(byId).toList());
        }
        return List.copyOf(order);
    }

    private static void validateDependencies(Set<UUID> taskIds, List<ScheduleDependencyInput> dependencies) {
        Set<String> pairs = new HashSet<>();
        for (ScheduleDependencyInput edge : dependencies) {
            if (edge.id() == null || edge.type() == null || edge.predecessorId() == null || edge.successorId() == null)
                throw new IllegalArgumentException("Dependency fields are required.");
            if (edge.predecessorId().equals(edge.successorId()))
                throw new IllegalArgumentException("A task cannot depend on itself.");
            if (!taskIds.contains(edge.predecessorId()) || !taskIds.contains(edge.successorId()))
                throw new IllegalArgumentException("Dependencies must connect tasks in this project.");
            if (edge.lagDays() < 0 || edge.lagDays() > 36500)
                throw new IllegalArgumentException("Dependency lag must be between 0 and 36500 calendar days.");
            String pair = edge.predecessorId() + ":" + edge.successorId();
            if (!pairs.add(pair)) throw new IllegalArgumentException("Only one dependency may connect the same ordered task pair.");
        }
    }

    private static long weight(ScheduleDependencyInput edge, Map<UUID, ScheduleTaskInput> tasks) {
        long predecessorDuration = tasks.get(edge.predecessorId()).durationDays();
        long successorDuration = tasks.get(edge.successorId()).durationDays();
        return switch (edge.type()) {
            case FS -> predecessorDuration + edge.lagDays();
            case SS -> edge.lagDays();
            case FF -> predecessorDuration + edge.lagDays() - successorDuration;
            case SF -> edge.lagDays() - successorDuration;
        };
    }

    private static LocalDate startDate(LocalDate origin, long offset) {
        try { return origin.plusDays(offset); }
        catch (DateTimeException exception) { throw new IllegalArgumentException("The calculated schedule exceeds the supported date range."); }
    }

    private static LocalDate finishDate(LocalDate origin, long startOffset, long duration) {
        return startDate(origin, startOffset + Math.max(0, duration - 1L));
    }
}
