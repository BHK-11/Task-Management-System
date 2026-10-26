# Phase 2 task management

## Request and ownership flow

Authenticated browser forms post to `TaskController`, `TaskComponentController`, or `TaskTagController`. Controllers get the owner UUID from `TaskOsUserDetails`; no form accepts an owner ID. `TaskService`, `ChecklistService`, and `TaskTagService` repeat ownership checks at the persistence boundary and run mutations transactionally. Each task lookup uses both its UUID and owner UUID. Tag assignment resolves tag UUIDs against the same owner. Bulk requests validate the full selected set before changing any item, are capped at 50 tasks, and commit or roll back as one operation.

## Stored model

- `Task` stores title, description, status, priority, date-only start/due values, lifecycle timestamps, archive state, a JPA version, and an immutable owner.
- `Task.parent` supports one level of subtasks. The service rejects a subtask as a parent, which prevents cycles and deeper hierarchies. Parent archive and delete operations include direct children.
- `TaskTag` is a personal, reusable label with a controlled color. Tags replace a separate category model in this phase.
- `ChecklistItem` belongs to one task and has explicit completion and ordering.
- `TaskActivity` records state changes and major edits. It stores a nullable task UUID rather than a task foreign key so hard-delete events remain available. The owner's account deletion still removes their activity records.

The Flyway V3 migration owns the task schema. Hibernate validates mappings. Status, priority, and activity event names use constrained strings; SQL `DATE` and Java `LocalDate` preserve date-only user intent. “Today” and “overdue” use the account's saved timezone. V4 adds task indexes for owner/status/due-date and owner/status/completion-time dashboard queries.

## Behavior

Task status changes use an allowlisted transition graph. Completion and reopening update `completed_at`; archive/restore preserve task content and add activity. Hard deletion requires an explicit confirmation in the UI, cascades to subtasks and checklist items, and writes activity before removing tasks. Forms bind to DTOs, never directly to JPA entities. Optimistic version checks prevent stale edit forms from silently overwriting newer task data.

The list query scopes by owner, archive view, status, priority, due-date window, tag, child presence, and an escaped case-insensitive search over titles, descriptions, and tag names. It applies allowlisted sorting and bounded pagination, then batch-loads visible task details and subtask counts. The task page shows the most recent 30 activity events.

All state-changing browser actions use CSRF-protected POST forms. The browser stores no task data. JavaScript only asks for confirmation before hard deletion and handles the existing navigation drawer and theme preference.

## Scope boundary

Phases 2–3 implement task CRUD, status and priority, date-only start and due dates, one-level subtasks, ordered checklists, tags, search/filter/sort/pagination, bulk actions, archive/restore, activity history, dashboard metrics, and personal date-based views. Projects, Kanban, recurring tasks, reminders, full calendar views, time tracking, and AI features remain outside the implemented scope.

## Verification limits

MockMvc integration tests run the V1–V4 migrations against H2 in PostgreSQL compatibility mode and exercise rendered task forms, dashboard views, and owner checks. This does not certify PostgreSQL behavior. `PostgreSqlFoundationTest` remains opt-in through `TASKOS_TEST_DB_URL`. See [verification.md](../testing/verification.md) for the current browser and PostgreSQL results.
