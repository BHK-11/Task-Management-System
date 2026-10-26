# Burndown reconstruction

At sprint start, `SprintBaselineItem` captures each selected task's ID, title, status, and nullable estimate. `Sprint.baselinePoints` stores the sum of estimated points (zero is included); unestimated work remains separately countable. The baseline is not overwritten when current task values change.

`AgileService.burndown` creates one point for each sprint date in UTC. It replays timestamped `SprintEvent` rows from the start baseline. Scope-added/removed, estimate-changed, task-completed, and task-reopened events change the reconstructed work state. Actual remaining points are `NULL` before the sprint starts and after the last recorded active date. They are never generated for future dates. The ideal line is a calendar-day reference from baseline to zero across the scheduled date range; it is not a forecast.

Active scope changes and estimate changes require a reason in the UI and retain it in the event record. Task completion/reopening comes from canonical status events. Sprint close stores summary values, and later task status changes do not rewrite completed velocity or the saved close metrics.

The chart is a lightweight SVG with a data table containing the same values for screen readers and users who do not interpret the graphic. UTC day boundaries are explicit; users in other timezones may see an event on a neighboring local calendar date.
