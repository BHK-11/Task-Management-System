# Phase 3 dashboard

## Request flow and ownership

`DashboardController` accepts authenticated browser requests and gets the account UUID from `TaskOsUserDetails`. `DashboardService` reads `UserAccount` for its saved timezone and passes the principal's UUID to every task and activity query. The dashboard does not accept an owner ID in a route or form. `TaskController` owns create and status POSTs; those continue through `TaskService` and its existing validation, ownership checks, status graph, activity events, archive rules, and optimistic locking.

The dashboard uses compact DTO records. It does not persist dashboard copies or expose user credentials or JPA graphs to Thymeleaf. Task rows include the parent task's ID, title, status, priority, due date, a display reason, and batched child progress. Recent activity is limited to eight owner-scoped events; task titles are loaded in one additional owner-scoped query. Deleted-task events retain their recorded summary and have no task link.

## Metric contract

All actionable metrics and task views use top-level tasks only. Subtasks remain part of the existing task hierarchy and are represented as progress on the parent; progress counts every direct child in the denominator, with only `COMPLETED` children in the completed count. An active task is not archived and has status `TODO`, `IN_PROGRESS`, or `ON_HOLD`. `COMPLETED`, `CANCELLED`, archived, and child tasks are excluded from actionable counts and lists. Recently completed rows and completion metrics include only unarchived top-level tasks whose current status is `COMPLETED` and whose `completedAt` is present.

| Value | Definition |
| --- | --- |
| Today | Active top-level task whose date-only due date equals the account's current local date. |
| Tomorrow | Active top-level task due exactly one local calendar day after today. |
| Upcoming metric | Active top-level task due tomorrow through today + 7 days, inclusive. Today is excluded. |
| Upcoming page | All active top-level tasks due after today, paged in due-date order. Tomorrow is its own group; the next group spans today + 2 through today + 7; Later starts after today + 7. |
| Overdue | Active top-level task with a due date before the account's local date. A task due today is never overdue earlier in that date. |
| Unscheduled | Active top-level task whose due date is null. |
| Completed today / last seven days | Currently completed, unarchived top-level task with `completedAt` inside the half-open timestamp interval for the current local day or the current day plus six preceding local dates. |

The seven-day chart reports the same current-completion state, bucketed by local calendar date. The task transition clears `completedAt` when reopened; old completion events may remain in activity history but do not contribute to the count or chart. The chart labels and table show actual task counts. Bar heights only scale those counts for visual comparison.

## Date and timezone behavior

`Clock` is injected from `TimeConfiguration` and defaults to UTC. `DashboardService` resolves the account's saved `ZoneId`, with UTC as a fallback for a missing or invalid stored value. It derives `ZonedDateTime` and `LocalDate` from `clock.instant()` and that zone. Due dates remain `LocalDate` values, matching the Phase 2 SQL `DATE` column; no midnight timestamp is stored for a date-only due date.

Completion timestamps use local midnight boundaries converted to `Instant`, with the end boundary excluded. The chart calculates each local day independently, so a daylight-saving date may cover 23 or 25 hours. The week window, upcoming horizon, leap days, and year boundaries use calendar-day arithmetic (`LocalDate.plusDays` / `minusDays`).

## Query and mutation design

The overview uses one owner-scoped aggregate for active/date/completion metric values. Task lists are database filtered and paged; the dashboard bounds My Day to eight, priority and upcoming previews to five each, recently completed tasks to five, and activity to eight. The date views use 30-row pages and cap the page number. Child progress is grouped by parent ID in a batch query, not fetched per row. The seven chart points use seven indexed count queries because each account-local midnight pair can have a different UTC duration. V4 adds `(owner_id, status, due_date)` and `(owner_id, status, completed_at)` indexes.

Quick Add posts the existing `TaskForm` to `/tasks`. Quick Complete and Reopen post the existing task status endpoint. The task service validates the owner and transition, writes the activity event, saves the state, and updates completion time. All requests retain Spring Security authentication and CSRF. Post-action return destinations are an allowlist; arbitrary redirect URLs are ignored.

No dashboard cache is used, so each request reads current task and activity state. The current record set is not benchmarked against PostgreSQL; query plans and latency still need measurement on the deployment database.

## Views and presentation

- `/dashboard` shows the authenticated greeting, local date, metric cards, My Day preview, priorities, upcoming tasks, activity, current completion trend, recently completed tasks, and quick add.
- `/my-day`, `/tasks/today`, `/tasks/tomorrow`, `/tasks/upcoming`, and `/tasks/overdue` provide focused, paginated task lists.
- `/app` redirects authenticated users to `/dashboard`; login success also goes to `/dashboard`.
- The dashboard uses the existing workspace shell and its theme preference. `dashboard.css` uses the shared CSS variables, visible focus states, a narrow-screen single-column layout, reduced-motion support, and a screen-reader data table for the chart.

There are no productivity scores, percentages, predictions, AI recommendations, or seeded demo tasks. The dashboard does not add dashboard customization settings; it uses the existing account timezone and theme preferences.

## Verification limits

The Phase 3 tests run against H2 in PostgreSQL compatibility mode and exercise Flyway V1–V4, owner isolation, CSRF, status transitions, date boundaries, DST, leap day, completion/reopen behavior, archive/cancel policy, child counting, and rendered templates. H2 passing does not certify the PostgreSQL query plan or migration behavior. The PostgreSQL integration test remains opt-in through `TASKOS_TEST_DB_URL`. See [verification.md](../testing/verification.md) for the latest run and browser status.
