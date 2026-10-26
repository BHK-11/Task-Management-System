# PostgreSQL and Flyway

## Connection settings

The application connects using the PostgreSQL JDBC driver. Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the process environment. The local profile provides only a local URL and username; it does not supply a database password. The `prod` profile requires all three settings.

The database must already exist. The configured database role needs permission to create the `task_os` schema and its tables. Hibernate uses `ddl-auto: validate`; Flyway owns schema changes, and no destructive reset is run.

## Migrations

Migration files are under `src/main/resources/db/migration` and use `V<number>__<description>.sql` names:

- `V1__create_application_schema.sql` creates the `task_os` schema.
- `V2__create_user_identity.sql` creates `users`, `roles`, `user_roles`, and `auth_events`, including unique email, check, index, and foreign-key constraints. It seeds the two global role names but no administrator account.
- `V3__create_task_management.sql` creates owner-scoped `tasks`, `tags`, task/tag assignments, checklist items, and task activity history. It adds indexes for owner/status, owner/due date, owner/archive state, parent tasks, tag lookup, checklist order, and activity history.
- `V4__add_dashboard_query_indexes.sql` adds composite indexes for owner/status/due-date and owner/status/completion-time queries.
- `V5__productivity_methodologies.sql` adds personal methodology classifications and daily planning records linked to canonical tasks.
- `V6__project_management_and_kanban.sql` adds private projects, workflow columns, canonical task placements, milestones, activity, templates, owner-safe foreign keys, and indexes.
- `V7__project_task_activity.sql` expands the project activity event constraint for task edits and lifecycle events.
- `V8__agile_scrum.sql` adds opt-in Scrum metadata, epics, acceptance criteria, sprints, membership snapshots, event history, ceremonies, daily notes, and impediments.
- `V9__retrospective_action_items.sql` adds retrospective actions with a unique optional link to a canonical task.
- `V10__definition_of_done_checklists.sql` adds task-specific persisted Definition of Done checks.
- `V11__project_scheduling.sql` adds project dependencies, durations, baselines, and scheduling history.
- `V12__time_management.sql` adds calendar blocks, focus sessions, time tracking, recurrence, reminders, and in-app notifications.
- `V13__goals_okrs_habits_reviews.sql` adds personal goals and measurements, milestones and canonical task links, OKR cycles/objectives/key results, effective-dated habit schedules, pauses/check-ins, reviews, and goal/habit reminder targets.
- `V14__team_workspaces_and_collaboration.sql` adds workspace/membership/invitation/activity records and explicit shared task/project scope.
- `V15__workspace_notification_preferences.sql` adds per-member assignment, mention, and activity notification preferences.
- `V16__track_shared_task_creation.sql` adds task-creation events to the workspace activity constraint.

Tasks store status and priority as constrained strings, dates as SQL `DATE`, and creation/update/completion/archive times as timestamps with timezone. A task's owner is immutable. The service enforces one-level subtasks and only permits owner-matched tags. Deleting a task cascades to its subtasks and checklist items; activity rows retain a nullable raw task UUID so a deletion event survives hard deletion.

Identity records use UUID primary keys. Emails are trimmed and lowercased in the Java registration and lookup flow; the database unique constraint enforces uniqueness after normalization. Account preferences store controlled theme, timezone, and locale values.

Authentication audit events store a nullable user UUID, event type, and timestamp. Failed-login records do not store the attempted email or IP. Events older than 90 days are purged daily.

## Profiles and tests

Normal `local` and `prod` application starts use PostgreSQL. The `test` profile defaults to a named, in-memory H2 database in PostgreSQL compatibility mode so Flyway and JPA mappings can be tested without touching a local database. This verifies migration execution against H2; it is not PostgreSQL integration verification.

`PostgreSqlFoundationTest` runs only when `TASKOS_TEST_DB_URL` matches `jdbc:postgresql:.*`. Point it at a dedicated test database. Its URL, username, and password must not point to production data. It verifies the PostgreSQL product, `task_os` schema, Flyway V13, identity, and Phase 9 tables. When no dedicated PostgreSQL URL is configured, state that PostgreSQL integration remains unverified.
