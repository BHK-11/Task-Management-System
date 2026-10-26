# Verification records

## Phase 9 (25 October 2026)

- `.\mvnw.cmd test`: passed. Surefire ran 77 tests: 76 passed and the opt-in PostgreSQL integration test was skipped because `TASKOS_TEST_DB_URL` is not configured.
- Flyway V1–V13 and Hibernate mappings executed on H2 2.4.240 in PostgreSQL compatibility mode. V13 adds goals, OKRs, habits, reviews, and goal/habit reminder targets. H2 does not certify PostgreSQL migration or locking behavior.
- New automated coverage verifies increasing/decreasing progress math, real goal measurement history, missing versus zero OKR values, selected-weekday and weekly/monthly streak units, open current periods, pause behavior, quantity aggregation, effective-dated schedule changes, owner isolation, goal/habit reminder integration, CSRF, and authenticated Phase 9 page rendering.
- The full Phase 0–8 regression suite passed in the same run. A previously stale roadmap catalog assertion was updated to the currently available features.
- PostgreSQL tests, live embedded-server startup, browser end-to-end flows, responsive visual QA, keyboard/screen-reader audit, and performance benchmarks were not verified. `spring-boot:run` reached application-context initialization with the H2 test classpath, but Tomcat failed to start its Windows NIO selector (`SocketException: Invalid argument: connect`) on both the installed JDK 25 and JDK 21. Phase 9 templates and CSRF behavior were checked with MockMvc instead.
- Reminder delivery remains in-app only. No email or push provider is configured. The application does not include team OKRs, AI coaching, or predictive analytics.

## Phase 8 (25 October 2026)

- `.\mvnw.cmd test`: passed. Surefire ran 67 tests: 66 passed and the opt-in PostgreSQL integration test was skipped because `TASKOS_TEST_DB_URL` is not configured.
- Flyway V1–V12 and Hibernate mappings executed on H2 2.4.240 in PostgreSQL compatibility mode. The migration adds time blocks, focus sessions and intervals, time entries and segments, recurrence rules and occurrences, reminders, and in-app notifications.
- New tests cover authenticated rendering of all Phase 8 pages and calendar views, timezone DST gaps/overlaps, recurrence month-end/weekday rules, owner isolation, block conflicts, timer/focus lifecycle, due recurrence idempotency, reminder delivery idempotency, and tracked task-time totals.
- `node --check` passed for `calendar.js`, `focus.js`, and `time.js`.
- PostgreSQL migration/locking behavior, live application startup, browser drag-and-drop, responsive visual QA, accessibility audit, and performance benchmarks were not run. H2 and MockMvc results do not certify PostgreSQL or live browser behavior.

## Phase 6 (24 October 2026)

- `.\mvnw.cmd test`: passed. Surefire ran 49 tests: 48 passed and the opt-in PostgreSQL integration test was skipped because `TASKOS_TEST_DB_URL` is not configured.
- Flyway V1–V10 and Hibernate mappings executed on H2 2.4.240 in PostgreSQL compatibility mode. Flyway warned this H2 version is newer than its latest verified release (2.3.232).
- Agile integration coverage includes rendered Scrum pages, canonical task reuse, backlog filters and ordering, epics, acceptance and Definition of Done checklists, ceremonies, retrospective action conversion, active-sprint scope/estimate events, burndown, saved velocity, carryover, CSRF, ownership, and concurrent sprint starts. Project regression tests cover board placement and canonical status after column removal.
- `node --check src/main/resources/static/js/agile.js`: passed.
- PostgreSQL migration/locking behavior and live browser visual QA were not verified. H2 and MockMvc results do not certify PostgreSQL behavior or browser layout. The existing Windows/JDK Tomcat loopback limitation is documented above; this phase was verified with MockMvc.

## Phase 3 (20 October 2026)

- `.\mvnw.cmd clean package`: passed. Maven ran 29 tests: 28 passed and the opt-in PostgreSQL integration test was skipped because `TASKOS_TEST_DB_URL` is not configured. The packaged application is `target/task-os-0.0.1-SNAPSHOT.jar`.
- Flyway V1–V4 and dashboard repository queries executed in H2 2.4.240 PostgreSQL compatibility mode. Flyway warned that this H2 release is newer than its latest verified release (2.3.232).
- The eight dashboard integration tests cover owner-scoped counts and activity, My Day ordering/deduplication, completed/archive/cancelled and child-task policy, DST local-day boundaries, leap day, invalid timezone fallback, upcoming group boundaries and pagination, rendered date views, authentication, CSRF, quick add, complete, and reopen. PageRenderingTest covers the empty dashboard and legacy `/app` redirect. Phase 0–2 regression tests also passed.
- PostgreSQL migration/query behavior and query plans remain unverified; no dedicated `TASKOS_TEST_DB_URL` was provided. H2 results do not certify PostgreSQL behavior.
- Live HTTP/browser testing could not be completed. `spring-boot:run` with the H2 test profile successfully connected to H2 and applied V1–V4, then Tomcat failed while creating its Windows NIO selector with `Unable to establish loopback connection`. MockMvc verified the populated dashboard, empty state, upcoming, today, tomorrow, overdue, and My Day templates. Responsive layout and theme contrast were not visually reviewed in a browser.
- No database latency or query-plan benchmark was run. Query work is bounded and uses owner/status/date indexes, but the stated performance targets still require measurement on PostgreSQL.

## Phase 2 (20 October 2026)

- `.\mvnw.cmd clean package`: passed. Maven ran 21 tests: 20 passed and the opt-in PostgreSQL integration test was skipped because `TASKOS_TEST_DB_URL` is not configured. The packaged application is `target/task-os-0.0.1-SNAPSHOT.jar`.
- The test profile applied Flyway migrations V1, V2, and V3 to H2 2.4.240 in PostgreSQL compatibility mode. Flyway warned that this H2 release is newer than its latest verified release (2.3.232).
- The four task integration tests cover task creation and rendering, owner isolation for reads and mutations, CSRF, mixed-owner bulk rejection, successful bulk completion, date persistence, tag ownership, one-level subtasks, checklist edit/completion, activity events, search, pagination, archive, restore, and deletion history. Existing identity, route, and security tests also passed.
- PostgreSQL-specific migration and connection behavior remain unverified; Docker and a dedicated `TASKOS_TEST_DB_URL` were unavailable. H2 passing does not certify PostgreSQL behavior.
- A live browser review was not completed. A prior local server startup attempt on this Windows/JDK 25 environment failed in Tomcat's NIO selector with `Unable to establish loopback connection`; MockMvc verified the rendered Thymeleaf pages instead. Responsive behavior was implemented in CSS but not visually reviewed in a browser.

## Phase 1 (20 October 2026)

- The supplied source tree had no Git metadata, so no commit-based rollback point was available. The Phase 0 verification record below is the baseline evidence.

- `.\mvnw.cmd clean package`: passed. The suite ran 17 tests: 16 passed and one opt-in PostgreSQL integration test was skipped because `TASKOS_TEST_DB_URL` is not configured. The packaged application is `target/task-os-0.0.1-SNAPSHOT.jar`.
- The H2 test profile applied Flyway migrations V1 and V2 and verified the identity schema and seeded roles. The authentication tests cover registration and validation, password hashing, role assignment, CSRF, sign-in, session rotation and concurrency limits, protected profile access, account preferences, password changes, logout, generic credential errors, disabled accounts, and login throttling.
- MockMvc verifies the Thymeleaf pages, public preview, and access control. A live HTTP/browser review was attempted with the H2 profile, but embedded Tomcat could not start on this Windows/JDK 25 environment because its NIO poller failed to establish a loopback connection (`Unable to establish loopback connection`). This prevents a live visual review in this environment.
- The PostgreSQL migration and production database connection were not verified. H2 execution does not establish PostgreSQL compatibility. Configure `TASKOS_TEST_DB_URL` for the dedicated PostgreSQL integration check.

## Phase 0 baseline

## Environment results (20 October 2026)

- `java -version`: Temurin OpenJDK 25.0.4.1. Maven compiles the project with `--release 21`.
- `.\mvnw.cmd test`: passed, 8 tests total; 7 passed and one PostgreSQL integration test is skipped because `TASKOS_TEST_DB_URL` is not set.
- `.\mvnw.cmd package`: passed and produced `target/task-os-0.0.1-SNAPSHOT.jar`.
- Spring MVC/Thymeleaf rendering: verified for `/`, `/about`, `/app`, and `/roadmap`; static CSS serving and the safe 404 response are covered by MockMvc tests.
- Health endpoint: its safe status-only response is covered through a mock-servlet test with database auto-configuration excluded. This verifies endpoint configuration, not database health.
- Normal packaged application startup: attempted. PostgreSQL responds on localhost, but the server requires SCRAM authentication and no `DB_PASSWORD` is configured. Flyway therefore cannot connect and startup stops before serving HTTP.
- A browser review used the actual Thymeleaf-rendered landing and workspace HTML from MockMvc plus the project CSS/JavaScript assets. Light and dark styling, theme selection, drawer opening, Escape-to-close, focus return, and navigation visibility were checked. The landing page was checked at 320, 768, and 1440 pixels; the workspace at 320, 375, and 1280 pixels. The pages had no horizontal overflow at those tested widths.
- A web-only packaged preview was attempted with persistence auto-configuration excluded. The Windows Tomcat NIO selector failed to establish its loopback pipe (`Unable to establish loopback connection`), so the visual review used the rendered MockMvc snapshots rather than a live Spring Boot HTTP server.
- PostgreSQL migration execution and the migration history row are not verified. The opt-in integration test is ready for a dedicated test database.

A Maven build, MVC test, or mock health test does not establish PostgreSQL connectivity. Keep build, tests, application startup, HTTP rendering, migration execution, and browser review separate in any later verification update.

PostgreSQL integration coverage is opt-in through `TASKOS_TEST_DB_URL`, so it cannot pass accidentally against an in-memory database. If no PostgreSQL server is available, state that the integration check is skipped and the database criteria remain unverified.
# Phase 10 workspace verification (2026-10-26)

- `.\mvnw.cmd -q clean package` — passed: 86 tests, 0 failures, 0 errors, 1 skipped; packaged `target/task-os-0.0.1-SNAPSHOT.jar`.
- Flyway applied all 16 migrations against the test H2 database; Hibernate initialized the collaboration repositories and mappings.
- Coverage includes intended-recipient and one-use invitations, revocation, role and last-owner rules, simultaneous invitation acceptance, workspace isolation, personal-task route exclusion, assignment/mention/activity notification preferences, XSS escaping, member-removal revocation, archived-workspace read-only rules, shared Kanban task creation and movement, shared Scrum sprint access, status-to-Kanban synchronization, workspace selector rendering, calendar views, and project-share disclosure rendering.
- `PostgreSqlFoundationTest` was skipped because `TASKOS_TEST_DB_URL` was not configured. The PostgreSQL migration and browser end-to-end, responsive visual, and screen-reader behavior remain unverified. The live preview could not be opened: embedded Tomcat fails to establish its Windows loopback selector, so page rendering is covered through MockMvc.