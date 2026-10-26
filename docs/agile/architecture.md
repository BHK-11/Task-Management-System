# Agile and Scrum architecture

Phase 6 adds an optional Scrum view over Phase 5 projects and Phase 2 tasks. `AgileController` resolves the signed-in account and delegates to `AgileService`; the service owns validation, authorization, transactional changes, metrics, and event recording. Thymeleaf renders the workspace, while `static/js/agile.js` enhances backlog ordering and the existing project board script handles sprint card movement.

## Persistent model

Flyway `V8__agile_scrum.sql` adds the core Agile model. `V9__retrospective_action_items.sql` adds convertible retrospective actions, and `V10__definition_of_done_checklists.sql` adds per-task checklists. Together they create:

| Data | Role |
|---|---|
| `agile_project_settings` | Opt-in flag and project Definition of Done guidance, keyed by project. |
| `agile_work_items` | Agile type, nullable story points, epic link, item DoD notes and backlog position, keyed by canonical `task_id`. |
| `acceptance_criteria` | Ordered, editable, satisfiable criteria attached to a canonical task. |
| `epics` | Project grouping metadata; epics do not create task records. |
| `sprints` | Goal, dates, optional capacity, lifecycle and saved close metrics. |
| `sprint_memberships` | Historical membership state for a task in each sprint. |
| `sprint_baseline_items` | Immutable title, status and estimate snapshot captured at sprint start. |
| `sprint_events` | Timestamped scope, estimate, completion, reopening, ceremony and lifecycle events. |
| `sprint_ceremonies`, `sprint_daily_notes`, `sprint_impediments`, `sprint_action_items` | Review/retrospective notes, daily notes, blockers, and idempotently convertible retrospective actions. |
| `agile_definition_of_done_items` | Per-task check state copied from project checklist guidance and supplemented with item-specific checks. |

`ProjectTaskPlacement` remains the project-to-task relationship, and `TaskService` remains the canonical create/status engine. Agile records do not copy task titles, descriptions, priority, or status as a competing task model.

## Access and consistency

Routes are under `/projects/{projectId}`. Services resolve the project using the signed-in owner ID before reading or changing Agile data. Task links are checked against an owner- and project-scoped placement. Mutations are CSRF-protected POSTs. Archived projects are read-only. Starting a sprint locks the project row before checking for another active sprint and changing the state, serializing concurrent starts for that project.

Project task status changes publish the existing `TaskStatusChangedEvent`. `AgileService` records completion/reopening events for active sprint members; the project service synchronizes the Kanban placement in the same transaction. Moving an active sprint card uses the project board's existing workflow and WIP validation.

## Routes

- `/projects/{id}/agile`: enablement and Scrum entry point.
- `/projects/{id}/backlog`: search and filter canonical project tasks.
- `/projects/{id}/epics`: project epics and derived progress.
- `/projects/{id}/sprints`: planned, active, completed and cancelled sprints.
- `/projects/{id}/sprints/{sprintId}/planning` and `/active`: planning and execution.
- `/projects/{id}/sprints/{sprintId}`: completed sprint summary.
- `/review`, `/retrospective`, `/daily`, `/history`, and `/velocity`: ceremony notes, daily check-ins, event history and completed sprint metrics.

## Limits

This is a single-account project model. It does not provide team membership, invitations, shared decisions, planning poker, or team workload. Project Definition of Done guidance is entered one checklist item per line and copied to each task the first time the backlog loads it; task-specific check state is then stored independently. Changes to project defaults are not pushed into initialized checklists. These checks are user-confirmed, not CI verification. Review notes do not certify approval.

## Verification

Run `./mvnw test` (or `./mvnw.cmd test` on Windows). The tests use H2 in PostgreSQL mode. PostgreSQL-specific migration and locking behavior still needs a dedicated `TASKOS_TEST_DB_URL` run.
