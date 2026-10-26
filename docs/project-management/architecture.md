# Project management architecture — Phase 5

## Request and ownership path

`ProjectController` resolves the signed-in `TaskOsUserDetails` UUID and calls `ProjectService`. The service uses owner-scoped repository queries for projects, templates, tasks, and columns. No project owner ID is accepted from a browser form. A project UUID alone does not grant access.

`ProjectService` owns transactional project, board, milestone, template, progress, and activity behavior. `TaskService` remains the single task creation/status engine. `TaskStatusChangedEvent` is published after a successful canonical task transition; the synchronous project listener updates the placement before the task transaction commits.

## Persistent model

Flyway `V6__project_management_and_kanban.sql` adds the project tables and constraints. `V7__project_task_activity.sql` expands the activity event constraint for canonical task edits and lifecycle changes. The schema includes:

- `projects`: owner, lifecycle status, description, schedule, color, archive timestamp, and optimistic version.
- `project_board_columns`: owner-scoped ordered workflow columns and WIP policy.
- `project_task_placements`: one row per canonical `task_id`, with project, column, optional milestone, position, and version.
- `project_milestones`: per-project checkpoints, dates, lifecycle status, and order.
- `project_activity`: owner, actor, optional canonical task, event type, summary, and timestamp.
- `project_templates`: owner, display metadata, schema version, and a typed JSON definition string.

Composite foreign keys tie placements to the same owner’s task and project and tie columns/milestones to their project. Deleting a project removes placements and project metadata through explicit detach and project-level cascades; it never deletes task rows. Deleting a canonical task cascades its placement, consistent with task deletion.

## Pages

- `/projects`: paginated search and filters.
- `/projects/new` and `POST /projects`: project creation, default board setup, and optional template application.
- `/projects/{id}`: computed overview and links to the same task manager records.
- `/projects/{id}/board`: filtered board, task create/add, ordering/movement, milestone assignment.
- `/projects/{id}/milestones`: milestone management and task-derived progress.
- `/projects/{id}/activity`: latest 30 persisted project events.
- `/projects/{id}/settings`: project lifecycle, workflow columns, and template save.
- `/projects/templates`: preview, use, and delete the signed-in owner’s templates.

All state-changing routes are POST requests and use the existing Spring Security CSRF protection. The board includes native drag-and-drop for pointer users and a labeled destination/position form for keyboard and touch users.

## Limits and exclusions

The board caps workflow columns at 12, milestones at 50, task examples per template at 100, and available-task choices at 250. Progress queries are derived from placed canonical tasks. Scrum/sprints, Gantt scheduling, teams, and project-level time tracking are not included.
