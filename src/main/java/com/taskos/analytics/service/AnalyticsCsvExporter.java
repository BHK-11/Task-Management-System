package com.taskos.analytics.service;

import com.taskos.analytics.dto.AnalyticsOverview;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsCsvExporter {
    public String export(AnalyticsOverview data) {
        StringBuilder csv = new StringBuilder("section,metric_id,label,value,unit,period,source,definition_version\r\n");
        String period = data.start() + " to " + data.end();
        for (var definition : data.definitions()) {
            String value = switch (definition.metricId()) {
                case "TASK_CREATED" -> String.valueOf(data.createdTasks());
                case "TASK_COMPLETED_CURRENT" -> String.valueOf(data.completedTasks());
                case "TASK_COMPLETION_EVENTS" -> String.valueOf(data.completionEvents());
                case "TASK_OPEN_CURRENT" -> String.valueOf(data.openTasks());
                case "TASK_OVERDUE_CURRENT" -> String.valueOf(data.overdueTasks());
                case "TASK_DUE_TODAY" -> String.valueOf(data.dueTodayTasks());
                case "TASK_DUE_NEXT_7_DAYS" -> String.valueOf(data.dueNextSevenDaysTasks());
                case "TASK_WITHOUT_DUE_DATE" -> String.valueOf(data.noDueDateTasks());
                case "ACTUAL_TRACKED_TIME" -> data.actualWorkSeconds() == null ? "Unavailable for project filter" : String.valueOf(data.actualWorkSeconds());
                case "ACTIVE_PROJECTS" -> String.valueOf(data.activeProjects());
                case "ACTIVE_GOALS" -> String.valueOf(data.activeGoals());
                case "ACTIVE_HABITS" -> String.valueOf(data.activeHabits());
                case "AI_PLANS" -> String.valueOf(data.aiPlans());
                case "AI_VERIFIED_TASKS" -> String.valueOf(data.verifiedAiTasks());
                case "AUTOMATION_RUNS" -> String.valueOf(data.automationRuns());
                case "AUTOMATION_SUCCEEDED" -> String.valueOf(data.automationSucceeded());
                case "AUTOMATION_FAILED" -> String.valueOf(data.automationFailed());
                case "AUTOMATION_RETRIED" -> String.valueOf(data.automationRetried());
                default -> "See breakdown";
            };
            row(csv, "metric", definition.metricId(), definition.name(), value, definition.unit(),
                    period, definition.source(), definition.version());
        }
        data.statusDistribution().forEach(bucket -> row(csv, "status", "TASK_STATUS_CURRENT", bucket.label(),
                String.valueOf(bucket.count()), "tasks", "Current", "Task.status", version(data, "TASK_STATUS_CURRENT")));
        data.openPriorityDistribution().forEach(bucket -> row(csv, "priority", "TASK_PRIORITY_OPEN", bucket.label(),
                String.valueOf(bucket.count()), "tasks", "Current", "Task.priority, Task.status", version(data, "TASK_PRIORITY_OPEN")));
        data.completionTrend().forEach(point -> row(csv, "completion_trend", "TASK_COMPLETION_EVENTS", point.label(),
                String.valueOf(point.count()), "events", point.startDate() + " to " + point.endDate(),
                "TaskActivity.TASK_COMPLETED", version(data, "TASK_COMPLETION_EVENTS")));
        return csv.toString();
    }

    private static String version(AnalyticsOverview data, String metricId) {
        return data.definitions().stream().filter(definition -> definition.metricId().equals(metricId))
                .map(definition -> definition.version()).findFirst().orElse("");
    }

    private static void row(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(quote(values[i]));
        }
        csv.append("\r\n");
    }

    static String quote(String value) {
        String safe = value == null ? "" : value;
        String trimmed = safe.stripLeading();
        if (!trimmed.isEmpty() && ("=+-@".indexOf(trimmed.charAt(0)) >= 0
                || trimmed.charAt(0) == '\t' || trimmed.charAt(0) == '\r' || trimmed.charAt(0) == '\n')) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
