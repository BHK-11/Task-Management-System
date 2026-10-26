# Sprint lifecycle

Sprints belong to one project and move through `PLANNED`, `ACTIVE`, `COMPLETED`, or `CANCELLED`. A sprint requires a name, goal, valid inclusive date range, and may store planning notes and a non-negative capacity estimate. The maximum configured span is 90 days. Dates and daily chart buckets use UTC. Planned date overlaps are surfaced as guidance rather than prohibited.

Planning membership references canonical project tasks. A task cannot be in another current planned/active sprint at the same time. Starting requires the current UTC date to fall in the sprint range and at least one eligible, non-completed, non-cancelled, non-archived item. The service locks the project before checking the active-sprint invariant, captures a baseline snapshot and activates memberships in one transaction.

Active-sprint scope changes require a reason and are recorded. Work can be added, removed, or re-estimated. Task status still comes from the canonical Task service, and board moves use project workflow and WIP rules.

Completing a sprint saves delivered points, final scope, remaining points, and item counts. Completed membership states are preserved. Incomplete items can stay in the project backlog or be assigned to a planned sprint; their points count only when completed in a later sprint. Cancelling requires a reason and closes current memberships without marking work complete. Completed sprints cannot be restarted or edited as active work.

Sprint reviews are available during or after an active sprint; retrospectives require a completed sprint. Daily notes and blockers are available while active. A retrospective can hold tracked action items and convert each one into a canonical project task; repeating conversion returns the same task ID. Conversion creates a To Do task through the existing project/task service and does not schedule it into a sprint. These are notes in this single-account application, not a team chat or shared ceremony system.
