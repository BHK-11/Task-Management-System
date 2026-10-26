# Kanban board behavior

## Placement and movement

`ProjectTaskPlacement` is project-specific board metadata keyed by `task_id`; it is not a task copy. The unique task ID means one active placement per canonical task. A board move validates task ownership and project ownership, validates the destination column, checks WIP, updates the placement position, and calls `TaskService.transition` in the same transaction. Any failure rolls back both changes.

Positions are contiguous per column after each move or removal. Reorder operations first move rows to a temporary range, flush, and then persist positions starting at zero to avoid transient unique-index collisions. Archived task placements remain stored; move operations include those hidden rows when compacting so the database’s `(project_id, column_id, position)` uniqueness is preserved.

The browser can drag cards between columns and sends a CSRF-protected form request. Each card also provides an explicit column selector and position input, so moving does not depend on dragging. The server remains authoritative; a redirect reloads persisted state or displays the service error.

## Canonical status synchronization

Moving a card chooses the `TaskStatus` mapped by the destination stage. A task status transition made from another TASK OS view publishes `TaskStatusChangedEvent`; the project listener selects the preferred matching column and updates the placement in the same transaction. Task edits, archive, restore, and deletion also feed project activity history from the existing task activity stream. A task without a placement is ignored. If a custom column already represents the new status, it remains selected.

The event is published only for a successful status change. Creating a new project task already supplies the chosen column’s status to `TaskService.create`, then creates one placement for the returned canonical ID.

## WIP limits

Columns support `NONE`, `ADVISORY`, and `STRICT`. The default In Progress column has an advisory limit of three. WIP counts exclude archived, completed, and cancelled tasks. Advisory limits display a warning; strict limits reject a move or addition that would exceed the configured count.

Project mutations lock the project row with `PESSIMISTIC_WRITE`; the target count and placement write occur under that lock in the transaction. External task status changes pass through the same project lock in the event listener. H2 integration tests verify rejection and rollback, but concurrent PostgreSQL locking has not been tested in this environment.

## Custom workflow rules

Boards have at most 12 columns. Names are unique within a project. A workflow must retain a To Do or Backlog column, an In Progress or In Review column, and columns for On Hold, Done, and Cancelled so every canonical task status has a destination. Removing a column first moves its placements to the chosen destination; strict WIP is checked before the transfer.
