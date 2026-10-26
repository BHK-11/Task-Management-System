# TASK OS productivity methods

Phase 4 adds alternative ways to classify and select the same owner-scoped TASK OS tasks. A method adds metadata or a dated plan reference; it does not create a parallel task record. Task completion, priority, due date and archive state continue to come from the task engine.

## Method comparison

| Method | User decision | TASK OS behavior | Stored data |
| --- | --- | --- | --- |
| Eisenhower | Urgent and important, independently | Places active main tasks in Do, Schedule, Delegate or Eliminate; unclassified tasks remain visible | `task_productivity.urgent`, `important` |
| GTD | Capture, then convert, file, hold or discard; classify tasks into a list | Inbox captures stay separate until converted once through `TaskService`; converted tasks begin as Next Actions | `gtd_inbox_items`, `task_productivity.gtd_state`, waiting details |
| ABC | A/B/C category and relative rank | Saves user category and order; does not alter `TaskPriority` | `task_productivity.abc_category`, `abc_rank` |
| MoSCoW | Must, Should, Could or Won’t Have | Saves a scope category for each active main task | `task_productivity.moscow_category` |
| Impact / effort | Impact and effort scores from 1 to 5, optionally with a note | Sorts scored work by impact divided by effort; makes no claim about an 80/20 outcome | `task_productivity.impact_score`, `effort_score`, `impact_note` |
| Eat the Frog | One task to start with on a selected local date | User selects a task; a stable priority/due-date/update-time ordering offers candidates | `daily_plans.frog_task_id` |
| Ivy Lee | Up to six tasks in an order | The same task can be added once; explicit move-up/down controls save order | `daily_plans`, `daily_plan_entries` |
| 1-3-5 | Up to one Big, three Medium, five Small tasks | Server validates capacity; changing modes requires confirmation and explicit group assignments when converting from Ivy Lee | `daily_plans`, `daily_plan_entries` |
| GTD weekly review | Five review sections | Marks the week complete only while all sections are checked | `weekly_reviews` |

## Implementation

- `ProductivityController` provides authenticated Thymeleaf routes under `/productivity` and CSRF-protected POST operations.
- `ProductivityService` owns methodology rules, task ownership checks, dated plan limits, weekly review rules, stable suggestions and transactions.
- `TaskService` remains the only service used to create or transition canonical tasks. Inbox conversion is transactional and cannot be repeated after clarification.
- `TaskProductivity` is an optional one-to-one record keyed by the task ID. Its owner/task composite FK prevents a task's metadata from crossing owners.
- `GtdInboxItem`, `DailyPlan`, `DailyPlanEntry`, `WeeklyReview` and `ProductivityPreference` store independent inbox captures, dated references, review progress and user defaults.
- `TaskActivityType` records classification, capture, assessment, frog and plan events in the existing owner-scoped activity stream.
- `V5__productivity_methodologies.sql` creates the normalized tables, checks, ownership references, uniqueness rules and query indexes.

The task is still the source of truth for title, description, status, ordinary priority, due date and archive state. Completing a task through the existing task manager is immediately reflected in method pages because all methods resolve the shared task. Archived tasks leave active methodology views and are removed from active daily plans; restoring a task does not recreate a daily commitment. Permanent task deletion cascades its metadata and plan entries. A converted inbox record is retained as a completed clarification record even if its task is later deleted.

## Recommendations and user choice

Suggestions are deterministic and described in the hub. When user scores exist, TASK OS sorts by the recorded impact/effort ratio. Otherwise it can point to a HIGH or URGENT task due today or tomorrow. It returns “Not enough information for a recommendation” when neither basis exists. Suggestions never write classifications, invent scores, alter task priority or claim a productivity outcome.

Preferences store a primary method and default daily plan per user. Dates are `LocalDate` values; the default planning date comes from the account timezone. A user's standard task priority does not imply Eisenhower urgency or importance.

## Limits and verification

Method lists are owner-scoped and currently bounded to the first 250 active main tasks. The app offers accessible buttons for reordering rather than drag-only controls. Daily plans enforce limits and serialize capacity-changing writes by locking the user's row. H2-backed service and MVC tests validate migrations and core flows; H2 is not a substitute for a PostgreSQL integration run. Browser visual QA and multiple viewport checks remain separate from those automated tests.

The UI uses the current TASK OS theme and existing Spring MVC/Thymeleaf stack. `motion` is installed in the project npm manifest as requested; because there is no frontend build pipeline, the app's current transitions use small CSS effects and honor `prefers-reduced-motion` rather than loading Motion in the browser bundle.
