package com.taskos.scheduling.dto;

import com.taskos.project.entity.Project;
import com.taskos.scheduling.service.ScheduleResult;
import java.util.List;
import java.util.UUID;

public record ProjectSchedulePage(Project project, ScheduleResult schedule, List<TaskScheduleLine> tasks,
        List<DependencyLine> dependencies, List<DependencyGraphNode> graphNodes, int graphHeightPixels,
        List<MilestoneLine> milestones, List<ScheduleBaselineView> baselines,
        List<ScheduleImpactLine> baselineComparison, UUID selectedBaselineId, long axisDays,
        List<TimelineTick> timelineTicks, ScheduleChangePreview preview, String notice) {
    public ProjectSchedulePage {
        tasks = List.copyOf(tasks); dependencies = List.copyOf(dependencies); graphNodes = List.copyOf(graphNodes);
        milestones = List.copyOf(milestones);
        baselines = List.copyOf(baselines); baselineComparison = List.copyOf(baselineComparison);
        timelineTicks = List.copyOf(timelineTicks);
    }
}
