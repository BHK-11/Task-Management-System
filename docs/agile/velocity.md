# Story point velocity

At sprint close, delivered points are the estimates of active sprint members whose canonical tasks are `COMPLETED` at that time. The service saves the count on the sprint together with final scope, remaining points, and completed/incomplete item counts. A task completed in a later sprint earns points there; carryover does not award the same points twice.

Velocity history lists completed sprints in reverse chronological order. The displayed average uses up to the five most recently completed sprints and averages their saved `deliveredPoints`, including zero-delivery sprints. If no sprint is complete, no average is shown. Unestimated tasks are counted separately and contribute no points; `0` is an explicit estimate.

Velocity is retrospective planning guidance, not a promise or a conversion to hours. Cancelled sprints do not contribute. Reopening a task after sprint close does not change the saved sprint delivery value.
