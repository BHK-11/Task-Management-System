# Phase 15 implementation and repository audit

**Audit date:** 2026-10-26  
**Repository:** existing `outputs/task-os` workspace; no Git metadata was present.  
**Scope:** Phases 0–14 code, persistence, MVC routes, AI planner, current task workspace, documented limits, and available test/build setup. No production database was opened or changed.

## Readiness summary

The existing Java/Thymeleaf application has been extended for Phase 15; it was not rebuilt. The work manager accepts bounded English/Hindi/Hinglish text, can use supported browsers' speech recognition, produces a typed multi-job plan, and requires explicit approval. Approved work is persisted and queued, tasks go through `TaskService`, and generated documents/drafts are stored privately with owner-only integrity-checked downloads. The attached Phase 15 prompt ends mid-section 49 (“Organized Deliverables Workspace”), so this implementation covers the requirements available through that point; no unseen later requirements were assumed.

## Phase evidence

| Phase | Verified implementation | Status and boundary |
|---|---|---|
| 0 | Spring Boot application, Thymeleaf shell, PostgreSQL configuration, Flyway migrations, Maven wrapper | Foundation present. |
| 1 | Account registration/sign-in, password hashing, session controls, CSRF, login throttling | Implemented. Email verification, password recovery, and email changes need a delivery/verification flow. |
| 2 | Owner-scoped tasks, subtasks, checklists, tags, activity, filters, archive/restore, bulk operations | Implemented and integration-tested. |
| 3 | Personalized dashboard, local-date queries, task actions and analytics widgets | Implemented; query semantics are documented and tested. |
| 4 | Eisenhower, GTD, ABC, MoSCoW, Pareto, daily planning and reviews | Implemented in the productivity module. |
| 5 | Projects, canonical task placement, Kanban, milestones and templates | Implemented; project behavior and ownership tests pass. |
| 6 | Backlog, epics, sprint planning/execution, ceremonies, history, burndown and velocity | Implemented; lifecycle and ownership tests pass. |
| 7 | Project dependencies, critical path calculations, previews and baselines | Implemented. Resource leveling and advanced work calendars remain out of scope. |
| 8 | Calendar, time blocks, focus sessions, time tracking, recurrence and in-app reminders | Implemented; time-zone and lifecycle tests pass. No email/push delivery. |
| 9 | SMART goals, OKRs, measurements, scheduled habits and reviews | Implemented; calculation and boundary behavior is tested. |
| 10 | Workspaces, roles, invitations, shared tasks/projects, assignments, comments, notifications and workload counts | Core collaboration implemented. Invitation links are copy/paste; notifications are in-app and not real-time; some views cap at 100 without pagination. |
| 11 | Typed/versioned automation rules, outbox worker, leases, retries, approvals and execution history | Implemented for the documented trigger/action registry. No arbitrary code, external mail/webhooks, or full project/Scrum event coverage. |
| 12 | Private FastAPI planner, saved plans, edits, approval/rejection and transactional personal-task creation | Implemented. Phase 15 extends the work-manager path to 20,000 characters, multi-job plans and queued execution while retaining the legacy task-only route. |
| 15 | Voice/text work manager, typed multi-job plans, task creation, document generation, drafts, progress and downloads | First milestone implemented. No Gmail/WhatsApp sending, external agents, retries/cancellation, event stream, or product memory. Browser speech support depends on the user's browser and may use its configured speech service. |
| 13 | Owner-scoped analytics page/API and CSV export | Initial analytics implemented. Configurable reports, saved dashboards, scheduled PDF/workbook exports and prediction are not implemented; the feature catalog marks advanced reporting planned. |
| 14 | Theme tokens, desktop/tablet/phone navigation, command search, responsive shell and task workspace foundation | Foundation implemented. This is not a complete component-library rewrite; no browser visual-regression or screen-reader test suite exists. |

These statuses describe code and test evidence in this repository, not a claim that every originally proposed roadmap feature is complete. The live voice demo on the public home page is browser-only, unsaved, and disconnected from AI/task creation.

## Current architecture and dependencies

- **Application:** Java 21 target, Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA/Hibernate, Thymeleaf, Jakarta Validation, Actuator and Flyway.
- **Persistence:** PostgreSQL in normal/prod configuration; `ddl-auto=validate`, Flyway clean disabled, and 24 forward migrations through V24. Tests use H2 PostgreSQL-compatibility mode.
- **Backend size:** 440 Java source files compiled, 75 JPA repositories discovered, 24 Java test classes. Modules cover auth, tasks, dashboard, productivity, projects, agile, scheduling, time, goals/habits, collaboration, automation, analytics and AI.
- **Frontend:** Thymeleaf, application CSS and vanilla JavaScript. `package.json` has Motion 14.4.0 and GSAP 3.15.0, with a lockfile but no npm scripts or frontend build pipeline; checked-in vendor assets are served by the Java app.
- **Planner/generator:** separate Python 3.14 FastAPI service with Pydantic structured plans and the OpenAI Python client; deterministic DOCX/XLSX/PPTX/PDF/TXT generators use pinned Python libraries. The service has a shared token, four plan slots, two generation slots, 25-second legacy and 45-second work-plan timeouts, no retries, and no TASK OS database credentials.
- **Routes:** key surfaces include `/tasks`, `/projects/**`, `/projects/{id}/agile`, `/projects/{id}/gantt`, `/time`, `/calendar`, `/goals`, `/habits`, `/teams`, `/workspaces/**`, `/automations/**`, `/analytics`, `/api/analytics/overview`, `/ai`, and `/agents`. Authenticated routes default to requiring sign-in; mutating forms use CSRF-protected POSTs.
- **Agent tooling:** no Hermes, Docker or Podman executable is available on this host. No external agent, browser automation, memory service, or Phase 15 output-generation dependency was installed. The deeper source/license review is recorded in [AI agent integration](architecture/ai-agent-integration.md).

## Priority findings

### Phase 15 implemented in this first milestone

1. Reused owner-scoped AI plan/step persistence for a durable built-in queue; did not repurpose automation records.
2. Added a two-minute active-step lease, one action per worker tick, persisted step states, dependency blocking, and recovery of abandoned `RUNNING` work after the lease.
3. Expanded the typed contract to 20,000 instruction characters and 12 jobs. Java and Python both validate action types, content bounds and dependency DAGs.
4. Added approved task creation through `TaskService`, deterministic DOCX/PDF/XLSX/PPTX generation, and downloadable text drafts.
5. Added private artifact metadata, 10 MiB size limits, format/MIME checks, private file storage, SHA-256 checks at download, and owner-only authenticated routes.
6. Added browser speech recognition with an editable transcript, language choice and typed-input fallback. Raw audio is not sent to TASK OS; browser speech services may process it.

### Remaining Phase 15 and production work

- No progress event stream, user cancellation, retry button, or product-specific job deletion/retention policy. Independent jobs run one at a time per worker.
- Gmail/WhatsApp sending and external agent execution are unsupported. Email/message actions save `.txt` drafts only.
- The worker lease is not a full idempotency/reconciliation scheme for arbitrary external side effects; no such side effects are enabled.
- Configure a Devanagari-capable font for Hindi PDF output on hosts without a supported system font. The local Windows Mangal font was exercised without copying it into the repository.

### Should resolve before production rollout

- Run the opt-in PostgreSQL foundation/integration test against a dedicated test database; this audit only proves H2 behavior.
- Add browser viewport and keyboard/accessibility verification for the mobile workspace and Phase 15 approvals/downloads.
- Add a Java transitive-dependency vulnerability/SBOM scan to CI. `npm audit` reported zero issues for the Node dependency tree, but no Java SCA or penetration test was run.
- Establish Git history/backup before Phase 15 schema changes. This workspace has no `.git` directory, so there is no branch, diff, or commit-based rollback available here.
- Review the in-memory, per-process login throttle before horizontal scaling; its current limitation is documented and requires shared edge/service rate limiting across instances.

### Nice to have

- Replace the vendor-asset/npm duplication with one deliberate frontend asset workflow only if the UI grows enough to need it.
- Add pagination to capped workspace lists and timeline views when real usage needs it.
- Keep user memory, browser automation and third-party agents optional and disabled until each has account/workspace ACL tests, explicit consent, retention/deletion, and the isolation described below.

## Unified Task Execution Workspace

The existing `/tasks` page is the correct foundation and is retained. Selecting a task keeps the current list/filter route and displays its details in the same workspace. The detail shows status, priority, due date, notes, subtask progress, dependencies and focus actions. Priority and due date can now be edited inline along with the existing task form. Subtasks can be added, edited, reordered, completed and deleted through existing owner-scoped, CSRF-protected routes. Completion is optimistic but rolls back on an API error and announces the failure. Responsive CSS places the selected detail below its row on narrow screens.

**Fixes made in the task workspace:** previous/next links disappeared at a page edge, and priority/deadline were display-only. `TaskController.list` now fetches the adjacent filtered page only when needed and retains the active filter/page context. The selected task has a CSRF-protected inline edit form for priority and due date. Integration tests check cross-page navigation with active filters and persisted priority/date edits. The workspace has no task attachment storage/upload feature; the UI labels that clearly. Browser-level mobile rendering was not verified in this run.

## Phase 15 first milestone architecture

Keep Java as the system of record and approval authority. Extend the existing typed planner; do not create a second website or let a model pick arbitrary tools.

```text
English / Hindi / Hinglish instruction
  → bounded intent analysis in the existing Python planner
  → typed plan of up to 12 independent/dependent jobs
  → Java persists the plan and approval state
  → user approves the plan
  → Java selects a typed internal capability for each job
  → execute through TaskService or deterministic document generators
  → reopen/validate output and reconcile task IDs
  → persist verified artifacts and show owner-scoped downloads/drafts
```

**Implemented persisted lifecycle:** `PENDING_APPROVAL → QUEUED ↔ RUNNING → COMPLETED | PARTIALLY_COMPLETED | FAILED`, or `REJECTED`. Every step records its state and a safe user-facing result summary. Java stores this in `ai_plans` and `ai_plan_steps`; the same existing plan is queued one action at a time. A two-minute lease permits recovery after a worker exits during an action. The current UI polls the selected plan every 2.5 seconds. There is no append-only event stream or user cancellation yet.

**Initial typed job set:**

- `CREATE_TASK` → existing Java `TaskService`, personal scope first.
- `GENERATE_DOCX`, `GENERATE_XLSX`, `GENERATE_PPTX`, `GENERATE_PDF` → deterministic generators in the private Python service over bounded plain text. The service reopens generated files with their format libraries; Java checks required file structure, MIME/type, and size before storage.
- `DRAFT_EMAIL`, `DRAFT_MESSAGE` → downloadable plain-text draft files. No Gmail, WhatsApp, SMTP or external send tool.

**Artifact storage:** `AiArtifactStorageService` writes opaque-ID files to `${user.home}/.taskos/artifacts` or `TASKOS_ARTIFACT_DIRECTORY`, outside `static`. Metadata binds each artifact to its plan, step and owner. Downloads require the authenticated owner, use an attachment content disposition, cap reads at 10 MiB and compare both size and SHA-256 before returning bytes. A local Windows Devanagari font is auto-detected if present; other hosts need `TASKOS_PDF_FONT_PATH` for Hindi PDFs. No font binary is bundled.

**Trust boundary:** Java validates every plan, scope and approval, owns job state, and performs task writes. The planner gets only submitted instruction/context and a service credential, never database credentials. Select tools from a server-owned enum/registry; model output is untrusted data. Enforce timeouts, token/job/file/parallelism budgets server-side, keep provider keys in server secret storage, redact logs, deny external egress by default, and make approvals explicit. The first milestone can generate structured documents without any external agent runtime.

## Optional agent readiness

- **Hermes:** best first optional agent adapter only after per-run disposable isolation, no host mounts/database secrets, default-deny egress, a scoped Java tool proxy, and tested cancellation/budgets/approval/tenant isolation. It is not ready to enable on this host.
- **Browser Use:** a separate browser worker only for authorized browser-only tasks; fresh profile per job, strict domain/network allowlist, private-address denial, bounded downloads and human handoff. Do not import a personal browser profile.
- **AgentMemory:** development-session memory is not product memory. A future user-memory service requires opt-in, owner/workspace namespaces, ACL enforcement, consent, revocation, deletion and retention.
- **OpenViking:** defer pending AGPL review and the same tenant/ACL and lifecycle proof.
- **Scientific Agent Skills:** development skill files, not production workers; none is in this product's current scientific scope. Review a specific skill/script/license before use.
- **Diagram Design:** development-time documentation skill only. The existing architecture diagram/profile does not become a runtime dependency.

Codex skills remain tools for building TASK OS; they are not TASK OS production services.

## Verification run for this audit

| Check | Result |
|---|---|
| `.\mvnw.cmd -Dtest=TaskManagementIntegrationTest test` | Passed: 6 tests, 0 failures/errors/skips, including pagination context and inline priority/deadline persistence. |
| `.\mvnw.cmd clean verify` | Passed: 112 tests, 0 failures/errors, 1 skipped. PostgreSQL opt-in test skipped because `TASKOS_TEST_DB_URL` is unset. Built the Spring Boot jar. |
| `services/ai-api/.venv/Scripts/python.exe -m unittest discover -s . -p 'test_*.py'` | Passed: 15 tests. Includes actual DOCX/PDF/XLSX/PPTX generation/reopen checks, Hindi PDF with local font, request/auth limits, and XLSX formula-as-text behavior. Starlette/httpx emits a deprecation warning. |
| `node --check` across `src/main/resources/static/js/*.js` | Passed: 11 files, including the Phase 15 voice/progress client. |
| `npm audit --audit-level=low` and Python `pip check` | Passed: 0 Node vulnerabilities; no broken Python requirements. This is not a Java dependency/SBOM scan. |

Maven emitted HTTP 401 warnings while checking metadata from two optional feeds; dependency resolution and the full build still succeeded. H2 was newer than Flyway's latest verified H2 version. The full test run did not exercise live PostgreSQL, live provider calls, a real browser speech service, browser rendering, screen readers, mobile viewports, load, or an external agent sandbox. The repository contains no Git metadata; a source ZIP checkpoint was made before edits. No production database was modified.
