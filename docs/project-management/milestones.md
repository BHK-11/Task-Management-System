# Project milestones

`ProjectMilestone` stores an owner-through-project checkpoint title, description, target date, status, order, and completion timestamp. Service operations verify the owner-scoped project before changing a milestone. Deleting a milestone unlinks placements and preserves every canonical task.

Milestone statuses are `PLANNED`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED`, with transitions controlled by `MilestoneStatus`. A milestone can be linked to a project task from the board. Subtasks cannot be placed independently because they remain within the existing parent task hierarchy.

Progress is calculated from linked placements at read time. Archived and cancelled tasks are excluded; completed tasks remain in the denominator and numerator. An empty milestone reports 0%, not 100%. Project progress counts active top-level tasks, excluding archived and cancelled tasks. It reports completed/open/overdue totals, status counts, task completion percent, and milestone counts. Overdue uses the project owner’s timezone. These metrics are not stored as editable totals.
