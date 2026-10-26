# TASK OS — Java productivity workspace

TASK OS is a Java web application for personal productivity and project management. Phases 0–3 provide the application foundation, database-backed accounts, secure sign-in, persistent tasks, subtasks, checklists, tags, owner-scoped task views, and an authenticated dashboard. Phase 4 adds methodology classifications, GTD inbox handling, and daily planning. Phase 5 adds owner-scoped projects, configurable Kanban workflows, milestones, reusable templates, progress, and project activity while reusing canonical Task records. Phases 6–7 add Scrum and project scheduling. Phase 8 adds a personal calendar, time blocks, focus sessions, actual time tracking, recurring tasks, reminders, and time insights. Phase 9 adds personal SMART goals, OKRs, schedule-aware habits, and evidence-based reviews. Phase 12 adds an approval-first AI planner; Phase 15 extends it to a queued work manager for tasks, private document deliverables, and message drafts.

## Technology

- Java 21 language and bytecode target; use JDK 21 or newer.
- Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA, Hibernate, Thymeleaf, Jakarta Validation, Actuator, and Flyway.
- PostgreSQL for normal local and production application runs.
- Maven Wrapper and JUnit 5 / Spring Security Test / MockMvc.
- Optional Python FastAPI services support AI planning and read-only browser observation; neither is required to run the core Java site.

## Configure PostgreSQL

Create a database named `taskos` and a database role with permission to create the `task_os` schema and tables. Set credentials in the process environment. Do not commit local credentials.

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/taskos"
$env:DB_USERNAME = "taskos"
$env:DB_PASSWORD = "your-local-password"
```

`.env.example` is reference documentation only; Spring Boot does not load `.env` automatically. The `prod` profile requires the three environment values and marks the session cookie secure. Run production behind HTTPS.

## Run the application

```powershell
java -version
.\mvnw.cmd spring-boot:run
```

Open <http://localhost:8080>. Flyway applies forward-only migrations on startup. Migrations V1–V21 establish the application, identity, task, productivity, project, agile, time, goals, collaboration, and automation schemas. V22 adds owner-scoped AI plan and step history; V24 adds queued work-manager states and private artifact metadata. Existing databases are not reset.

There are no demo credentials or default administrator account. Create an account at `/signup`, then sign in at `/login`. The public workspace preview is at `/preview`; `/dashboard`, `/projects`, `/app`, profile, and account settings require authentication. Successful sign-in opens `/dashboard`; `/app` remains as a compatible redirect.

## Routes

- `/`, `/about`, `/roadmap`, and `/preview` — public product pages and guest preview.
- `/signup`, `/login` — public registration and login forms.
- `/dashboard`, `/my-day`, `/tasks/today`, `/tasks/tomorrow`, `/tasks/upcoming`, `/tasks/overdue` — authenticated dashboard and personalized task views.
- `/app`, `/profile`, `/settings/account`, `/settings/security` — authenticated compatibility entry and account pages.
- `/tasks`, `/tasks/new`, `/tasks/{id}` — authenticated task search, creation, and task details.
- `/tags` — authenticated personal tag management.
- `/productivity`, `/productivity/eisenhower`, `/productivity/gtd`, `/productivity/abc`, `/productivity/moscow`, `/productivity/pareto`, `/productivity/daily`, `/productivity/weekly-review` — authenticated productivity methods.
- `/projects` — private project hub with search, status, and archive filters.
- `/projects/new`, `/projects/{id}`, `/projects/{id}/board`, `/projects/{id}/milestones`, `/projects/{id}/activity`, `/projects/{id}/settings` — project creation and workspace.
- `/projects/{id}/agile`, `/projects/{id}/backlog`, `/projects/{id}/epics`, `/projects/{id}/sprints` — optional Scrum workspace, backlog, epics, sprint planning and execution.
- `/projects/{id}/sprints/{sprintId}/planning`, `/active`, `/history`, `/velocity`, `/review`, `/retrospective`, and `/daily` — sprint lifecycle, summaries, metrics, and ceremony notes.
- `/teams`, `/workspaces/new`, `/workspaces/{id}`, `/workspaces/{id}/members`, `/workspaces/{id}/workload` — private workspace hub, member management, and task-count workload views.
- `/invitations/{token}` — authenticated, recipient-bound invitation preview; accept or decline with a CSRF-protected POST. Invitation links are generated in-app; no email is sent.
- `/workspaces/{workspaceId}/projects/{projectId}`, `/workspaces/{workspaceId}/tasks/{taskId}`, `/assigned-to-me`, `/notifications` — explicit shared project/task views, assignment inbox, and workspace notification inbox.
- `/time`, `/calendar`, `/time/blocks`, `/time/tracking`, `/time/insights` — personal schedule, calendar views, reservations, work timers, manual entries, and time reports.
- `/focus/pomodoro`, `/focus/deep-work`, `/focus/history`, `/tasks/recurring`, `/reminders` — persisted focus workflows, recurrence rules, and in-app reminders.
- `/goals`, `/goals/new`, `/goals/insights`, `/goals/{id}/roadmap` — personal goals, measurements, links, milestones, and factual summaries.
- `/okrs`, `/okrs/new`, `/okrs/{id}` — personal review cycles, objectives, measured key results, and measurement history.
- `/habits`, `/habits/new`, `/habits/calendar`, `/habits/{id}` — personal habits, schedules, check-ins, pauses, and schedule-aware streaks.
- `/reviews/weekly`, `/reviews/monthly`, `/reviews/history` — period summaries and user-authored reflections.
- `/projects/templates` — owner-scoped template library, preview, and deletion.
- `/ai` — voice/text work planning, review and approval, queued execution, and owner-only deliverable downloads.
- `/browser` — authenticated, read-only observation of operator-approved public HTTPS origins through a private Playwright worker. No login, click, submit, upload, download, screenshot, Browser Use, or Hermes action is enabled.
- `/communications` — authenticated Phase 19 provider-status page. All listed providers are currently `NOT_CONNECTED`; there is no live inbox, provider OAuth, meeting transcript import, or message sending.
- `/logout` — CSRF-protected POST that invalidates the current session.
- `/actuator/health` and `/actuator/info` — restricted Actuator surface with safe health output.
- `/admin/**` — reserved for `ROLE_ADMIN`; no admin account management UI exists in this phase.

Unknown application routes require authentication by default. CSS, JavaScript, images, error pages, and the public pages remain accessible to guests.

## Optional private services

The Java site runs without Python or Node.js. The AI Planner and browser worker are separate, optional Python services with their own dependencies and loopback-only startup instructions. AI planning requires a configured provider/model and a matching private service token. Browser observations require an exact HTTPS origin that you control; the worker must not be exposed publicly. See [AI Work Manager setup](docs/ai/copilot.md) and [browser worker setup](docs/browser/architecture.md). Do not commit `.env` files, provider keys, service tokens, local databases, or generated artifacts.

Phase 19 currently includes only the honest provider-status page and the existing local draft-file workflow. Gmail, Outlook, WhatsApp Business, provider calendars, meeting artifact retrieval, OAuth, and external sending are not implemented or connected.

## Authentication and account behavior

- Email addresses are trimmed and lowercased before identity lookup and persistence. The database enforces uniqueness.
- Passwords require 12–256 characters and are stored as PBKDF2 hashes through a delegating Spring Security encoder. Raw passwords are never persisted or logged.
- New registrations receive only `ROLE_USER`. `ROLE_ADMIN` is seeded as an authority but cannot be granted from a public form; administrative assignment requires controlled database/deployment access.
- CSRF protection remains enabled. Login creates a server-managed session, rotates the session identifier, allows up to three active sessions per account, and uses a 30-minute inactivity timeout. Session cookies are HttpOnly and SameSite=Lax; the production profile adds Secure.
- Repeated failures are throttled in the current application process: ten failures for one email/network-address pair within 15 minutes return HTTP 429. The key is kept as an in-memory HMAC. This throttle resets on restart and does not coordinate across application instances; place shared rate limiting at a trusted edge before horizontal scaling.
- Changing a password verifies the current password, updates the hash, expires registered sessions, and requires a fresh sign-in.
- Profile edits use the signed-in principal, not a submitted account ID. Email is read-only because email change needs a verification flow.
- Account preferences currently support light/dark/system theme, five timezone choices, and English. The saved theme is applied on authenticated pages.
- Authentication audit events omit email, IP, passwords, and session secrets. Events older than 90 days are deleted daily.

Email verification, password recovery, remember-me, and email changes are not implemented because no email delivery provider is configured. There is no public or seeded administrator password.

## Tests

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

The `test` profile uses isolated in-memory H2 in PostgreSQL compatibility mode and runs all twenty-two Flyway migrations. Tests cover security, dashboard, task ownership, productivity methods, project flows, Kanban movement, Scrum lifecycle and metrics, WIP checks, milestones, templates, time-page rendering, timezone recurrence rules, time ownership/lifecycle behavior, goal/OKR progress, habit streak boundaries, and copilot page/plan validation. H2 results do not certify PostgreSQL behavior.

`PostgreSqlFoundationTest` runs only when `TASKOS_TEST_DB_URL` is set to a dedicated PostgreSQL test database URL. Also set `DB_USERNAME` and `DB_PASSWORD`; never point it at production data.

## Project structure

```text
src/main/java/com/taskos/
  auth/          Login, signup, password encoding, audit events, handlers
  security/      SecurityFilterChain, user lookup, login throttling
  user/          User and role entities, repositories, profile services and forms
  task/          Task, tag, checklist, activity entities, owner-scoped services and MVC controllers
  dashboard/     Owner-scoped dashboard queries, date handling, DTOs and MVC views
  productivity/  Methodology entities, owner-scoped services/repositories and MVC views
  project/       Project, workflow, task-placement, milestone and template domain/services/routes
  agile/         Scrum settings, backlog metadata, epics, criteria, sprints, event history and MVC routes
  scheduling/    Project durations, same-project dependency graph, CPM solver, previews and baselines
  time/          Owner-scoped calendars, time blocks, focus sessions, tracking, recurrence and reminders
  ai/            Typed work plans, owner-scoped approval/execution, and private verified deliverables
  goals/         SMART goals, historical measurements, OKRs, and weekly/monthly reviews
  habits/        Effective-dated schedules, check-ins, pauses, calendars, and streak calculation
  common/        Shared models and email canonicalization
  web/           Public pages, workspace routes, shared authenticated model
src/main/resources/
  application*.yml
  db/migration/  Forward-only Flyway SQL
  templates/     Thymeleaf auth, account, public, workspace, and task pages
  static/        Shared, task, dashboard, productivity, project and schedule CSS/JavaScript, plus TASK OS mark
docs/            Architecture, productivity, database, setup, OOP, and verification records
  services/ai-api/ Private FastAPI planner and bounded DOCX/PDF/XLSX/PPTX/text generator; no business-data access
  services/browser-worker/ Separate FastAPI + Playwright read-only page-observation worker
```

See [AI copilot setup and limits](docs/ai/copilot.md) before configuring the optional provider service.
See [Phase 18 browser architecture and limits](docs/browser/architecture.md) before enabling the optional browser worker.

## Task-management behavior

- Every task and tag is owned by the authenticated account. Reads, writes, bulk actions, and tag assignment resolve records through owner-scoped repository methods; client-supplied IDs never establish ownership.
- Start and due dates are calendar dates (`LocalDate` / SQL `DATE`). Overdue and due-today filters use the account timezone.
- Subtasks are limited to one level, so cycles cannot be formed through the service. Deleting a task also deletes its subtasks and checklist items; the activity stream retains deletion events.
- Tags provide flexible classification. A separate category system is not included.
- Task descriptions are capped at 6,000 characters, titles at 160, tasks at 10 tags, and a bulk operation at 50 selected tasks. The list page caps page size at 50.
- Deletion is permanent and requires a confirmation in the browser. Mutations use CSRF-protected POST requests.

## Dashboard behavior

- Dashboard counts include active top-level tasks only: `TODO`, `IN_PROGRESS`, and `ON_HOLD`; completed, cancelled, archived, and child tasks are excluded from actionable metrics. Child tasks appear as progress on their parent.
- Today and Tomorrow use the account's saved timezone and `LocalDate`. Upcoming count covers tomorrow through seven calendar days after today, inclusive. Overdue means an active top-level task has a due date before the user's local date.
- The completion card and seven-day chart count only tasks currently in `COMPLETED` state with a `completedAt` in the local reporting period. Reopening clears `completedAt`, so a previous completion is not double-counted. Recent completion and activity widgets show persisted records only.
- Dashboard lists are bounded and paginated. All dashboard repository queries take the authenticated owner ID; user IDs are not accepted from dashboard URLs. Quick create, complete, and reopen reuse the existing task service and CSRF-protected POST actions.
- Invalid saved timezones fall back to UTC. A shared injected `Clock` drives dashboard dates and greeting behavior.

## Productivity methods

See [Phase 4 methodology behavior](docs/productivity/methodologies.md) for data ownership, daily capacity rules, user decisions, recommendation limits, and verification caveats.

## Project management

- A project owns workflow and placement metadata, not duplicated task rows. Each canonical task has at most one project placement. Deleting a project detaches its tasks and preserves them in the personal task manager.
- Projects and related records are scoped to the authenticated owner. All mutations use CSRF-protected POST forms.
- Kanban stages map to the existing `TaskStatus` enum. Moving a card and changing its task status are committed together; status changes from other views synchronize the board through a synchronous Java event listener.
- Strict WIP moves lock the project row and validate the target column inside the transaction. H2 verifies the policy behavior, but does not establish concurrent PostgreSQL behavior.
- Project and milestone progress come from linked canonical tasks. Versioned typed template definitions can optionally seed fresh canonical task examples.
- Optional Scrum uses the same canonical project tasks, with separate estimates, ordered backlog metadata, epics, sprint membership, snapshots, and event history. A completed sprint's delivered points are saved at close; incomplete work is returned to the backlog or assigned to a planned sprint without earning velocity twice. Project deletion and task detachment are restricted when they would erase Scrum history.
- Project scheduling uses calendar-day durations and project-owned dependencies. Java calculates the forward/backward passes, float, and critical tasks; changes preview before save; baselines retain planning snapshots. It does not include resource leveling or advanced work calendars.

See [project architecture](docs/project-management/architecture.md), [Kanban behavior](docs/project-management/kanban.md), [workflow mapping](docs/project-management/workflows.md), [milestones](docs/project-management/milestones.md), and [templates](docs/project-management/templates.md).

See the [Agile/Scrum architecture](docs/agile/architecture.md), [product backlog](docs/agile/product-backlog.md), [user stories](docs/agile/user-stories.md), [epics](docs/agile/epics.md), [sprint lifecycle](docs/agile/sprint-lifecycle.md), [burndown](docs/agile/burndown.md), [velocity](docs/agile/velocity.md), and [sprint history](docs/agile/sprint-history.md).

See [project scheduling](docs/project-management/scheduling.md) for interval conventions, CPM equations, complexity, baselines, preview rules, and limitations.

See [calendar, focus and time tracking](docs/time/calendar-and-focus.md) for timezone policy, timer persistence, recurrence behavior, reminder delivery, and reporting semantics.

See [goal architecture](docs/goals/architecture.md), [SMART goals](docs/goals/smart-goals.md), [progress calculation](docs/goals/progress-calculation.md), [canonical task links](docs/goals/task-linking.md), [OKR measurements](docs/okr/measurements.md), [habit schedules](docs/habits/habit-engine.md), [streak rules](docs/habits/streak-algorithms.md), [habit timezone policy](docs/habits/timezone-policy.md), and [weekly/monthly reviews](docs/reviews/weekly-monthly.md) for Phase 9 behavior and limits.

## Documentation

- [Authentication request flow](docs/architecture/authentication.md)
- [Architecture overview](docs/architecture/overview.md)
- [Java OOP examples](docs/architecture/java-oop.md)
- [PostgreSQL and Flyway](docs/database/postgresql-and-flyway.md)
- [Task-management architecture](docs/architecture/tasks.md)
- [Dashboard architecture and metric definitions](docs/architecture/dashboard.md)
- [Analytics metric definitions and coverage](docs/analytics/metrics.md)
- [UI foundation and responsive navigation](docs/ui/foundation.md)
- [Windows setup](docs/setup/windows.md)
- [Project scheduling and critical path](docs/project-management/scheduling.md)
- [Phase 9 goal architecture](docs/goals/architecture.md)
- [Phase 9 OKRs](docs/okr/objectives-and-key-results.md)
- [Phase 9 habit engine](docs/habits/habit-engine.md)
- [Phase 9 reviews](docs/reviews/weekly-monthly.md)
- [Verification record](docs/testing/verification.md)
- [Phase 15 readiness and repository audit](docs/phase-15-readiness.md)
