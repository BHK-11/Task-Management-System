# Sprint history

The sprint list and history pages show persisted sprint records. A completed summary includes the start baseline, final scope, delivered points, incomplete work counts, UTC burndown rows, membership history, review/retrospective notes, and the sprint event timeline. A cancelled sprint remains visible with its cancellation record. Project-wide Agile activity is bounded to the latest 30 events; a sprint's own history is currently returned in full.

Scope, estimate, task status, and sprint lifecycle changes are timestamped in `sprint_events`. Scope-add, scope-remove, and estimate-change events are included in the scope-change count. Unfinished work at close is retained as `INCOMPLETE` membership and can be added to a planned sprint or left in the project backlog. Membership history preserves its original sprint association.

Project deletion and task detachment/reassignment are restricted when they could sever Agile history. Permanent deletion of an Agile task is also rejected. Completed sprint metric fields are saved at close, so current task edits do not rewrite historical velocity.

History is owner-scoped and available only through the project owner account. Multi-user audit actors, comments, corrections, and data export are not part of this phase. Sprint event history has no retention or pagination policy yet. Retrospective action items retain their sprint source and any converted canonical task link.
