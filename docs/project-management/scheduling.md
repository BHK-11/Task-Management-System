# Project scheduling

The scheduling workspace extends canonical project tasks. It does not create parallel task records. Open it at `/projects/{id}/gantt`; dependency management and the directed graph are at `/projects/{id}/dependencies`.

## Scheduling model

- Migration `V11__project_scheduling.sql` adds task duration metadata, same-project task dependencies, and saved schedule baselines. A duration is a positive integer from 1 to 36,500 **calendar days**. Story points are not read or converted.
- `Project.startDate` is the explicit schedule origin. The solver returns an incomplete readiness result until the project start and every project task duration are present. It does not invent estimates or partial critical paths.
- Task `startDate` is a preferred earliest-start date: it can delay the calculated start, and dependency constraints can delay it further. It is not a fixed date. Task `dueDate` remains a target and is not a hard finish constraint in this version.
- Work intervals are half-open: `[start, start + duration)`. A 3-day task starting on 5 January occupies 5–7 January; its internal finish offset is 3 and its displayed finish is 7 January. A zero-duration node can be supplied to the solver for an event, but the existing project milestone records are currently displayed as dated markers and are not dependency-graph nodes.
- Completed task timestamps remain actual completion evidence. The schedule never invents an actual start date. Status does not itself move a planned bar.

## Dependencies and algorithms

There is at most one dependency per ordered task pair. Both endpoints must be placed in the same owned project. Self-links, duplicate pairs, invalid lag, and cycles are rejected. Lag is a nonnegative whole number of calendar days.

For predecessor `i`, successor `j`, durations `d`, start offsets `s`, and lag `L`, the persisted relationship means:

| Type | Constraint |
| --- | --- |
| FS | `s_j >= s_i + d_i + L` |
| SS | `s_j >= s_i + L` |
| FF | `s_j >= s_i + d_i + L - d_j` |
| SF | `s_j >= s_i + L - d_j` |

The pure Java `CriticalPathCalculator` uses a stable Kahn topological sort (UUID order breaks ties), then applies the generalized edge weights in the forward pass. The backward pass works over reverse topological order and inverts each same edge constraint. Early/late dates, total float, critical tasks, and critical edges come from those passes. All independent roots and terminal tasks remain in the graph; multiple zero-float branches are retained.

With `V` tasks and `E` dependencies, deterministic topological sorting is `O((V + E) log V)` due to the priority queue. Forward and backward passes are `O(V + E)`, and graph storage is `O(V + E)`.

## Editing, previews, and baselines

Task duration and planned start edits use a preview-and-confirm flow. The preview changes only an in-memory solver input. Confirmation locks the project row, rechecks the project and task versions, validates the submitted values again, then saves the estimate and planned start. Dependency edits use the same project lock, so concurrent edge additions cannot each validate against a stale graph and create a cycle together.

Gantt bars may be dragged horizontally to propose an earliest start. Drop submits the proposed date to the same Java preview; it does not persist directly. The visible schedule editor is the keyboard alternative and also supports duration edits.

A baseline is a versioned JSON snapshot containing task planning values, calculated results when available, dependency rules, and milestone targets. The baseline entity has no update operation; changes to live tasks cannot rewrite the stored snapshot. Baselines can be compared to current dates, duration, and float. Deleting baselines is not exposed in this phase.

## Integration and limits

The Gantt reuses project board placements and task status. It includes active and planned sprint associations, existing milestone target markers, and task detail links. Sprint dates do not constrain CPM. Project milestone targets are visible but cannot yet be connected as dependency endpoints.

The calendar is calendar-day only. Due dates are informational, target finish is not a hard constraint, and the solver does not level resources, account for holidays, or calculate actual starts. A task's schedule status is independent of urgency, priority, productivity classifications, and sprint history.

The SVG dependency paths are a visual layer over persisted dependency rows; the accessible edge table provides the same relationships in reading order. The interface supplies day/week/month zoom and a horizontal-scroll layout, plus `prefers-reduced-motion` support.
