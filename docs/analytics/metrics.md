# Analytics metrics

`/analytics`, `/api/analytics/overview`, and `/analytics/export.csv` use the same owner-scoped `AnalyticsService` and metric registry (definition version `1.0`). Dates are inclusive local calendar dates; database timestamps use the corresponding `[start-of-start, start-of-day-after-end)` interval. A project filter is accepted only for a personal project owned by the authenticated user.

| Metric | Calculation | Source and limits |
| --- | --- | --- |
| Completion events | Count recorded `TASK_COMPLETED` events during the period. Reopened and completed tasks can produce multiple events. | `TaskActivity` joined to retained, unarchived, top-level personal `Task` records. |
| Currently completed tasks | Count tasks currently in `COMPLETED` with `completedAt` in the period. | Reopened tasks have their completion timestamp cleared and are excluded. |
| Tasks created | Count retained, unarchived, top-level personal tasks created during the period. | Physically deleted and archived tasks are not included. |
| Open tasks | Count current `TODO`, `IN_PROGRESS`, and `ON_HOLD` tasks. | Current task state; independent of the selected date range. |
| Overdue, due today, due next seven days, no due date | Count current open tasks using local date semantics. “Next seven days” starts tomorrow and includes today + 7. | Current task state; date independent. |
| Status distribution | Count all current canonical task statuses. | Includes cancelled; excludes child and archived tasks. |
| Open tasks by priority | Group current open tasks by canonical priority. | Priority is a user-assigned planning field. |
| Actual tracked time | Union of the user's recorded time segments clipped to the selected interval. | Running intervals stop at the current instant. Planned calendar blocks are not actual time. For a project-filtered page, this metric is unavailable because segment records have no project key. |
| Active projects, goals, habits | Current active records in personal scope. | Independent of selected dates. Habit adherence is not inferred here. |
| AI plans | Count saved plans created in the period. | Owner-scoped plan rows. |
| Verified AI tasks | Count completed plan steps with a persisted result task ID. | Saved plan execution outcomes, not generated suggestions. |
| Automation executions | Count personal-scope execution rows by owner and creation time. | A retry updates the same execution row. Retried counts executions with `attemptCount > 0`, not attempts. Workspace runs are excluded. |

Task counts exclude subtasks from the main status and trend metrics. Daily completion buckets are used for ranges up to 45 days; longer ranges use local Monday-to-Sunday buckets. If the range contains more than 10,000 completion events, the event count remains available but the chart is hidden rather than sampling the data.

The current schema does not support trustworthy estimation accuracy, historical Kanban snapshots, team workload rollups on this personal page, or project-filtered actual time. Sprint, Gantt, goal measurement, and habit adherence have separate source data but are not merged into this initial overview. No predictions or synthetic observations are displayed.
