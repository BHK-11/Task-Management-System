# Product backlog

The product backlog is the ordered list of eligible canonical tasks placed in one project. Enabling Scrum initializes Agile metadata for that project's existing placements; private backlog entries use `ProjectService`/`TaskService`, while shared-project entries use the workspace task service and retain the project's legacy owner key for the board placement. The `agile_work_items.task_id` primary key links estimate, type, epic, and position metadata to that task.

## Ordering and controls

Backlog position is separate from task priority. Move-up/move-down forms work without JavaScript, and `agile.js` adds pointer drag-and-drop that posts the submitted ordered task IDs. The service validates the submitted list against the project's current items, serializes edits using the project lock, and normalizes positions. Reloading the page preserves the saved order.

## Search and filters

`/projects/{id}/backlog` supports text search over task title/description and filters for work type, canonical task priority, epic, canonical task status, sprint membership (`BACKLOG`, `IN_SPRINT`, `ALL`), and unestimated items. The view follows backlog order. Estimate values are `0, 1, 2, 3, 5, 8, 13, 20`; `NULL` means unestimated and `0` is a real estimate. Points are not hours.

## Scope

Story, Task, Bug, and Spike are metadata classifications. Acceptance criteria are ordered records with edit, satisfy/reopen, reorder, and remove actions. Satisfying all criteria does not complete or approve a task. Project and item Definition of Done fields are guidance text. Kanban movement and task completion continue to use the canonical task engine.

Backlog results are assembled from the project's placements and Agile metadata in the service; the current view is not database-paginated. Very large project backlogs may need bounded queries and pagination before use at scale.
