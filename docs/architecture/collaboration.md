# Workspaces and collaboration

## Scope and privacy boundary

`Workspace` is an explicit collaboration boundary. Membership is keyed by workspace and account, so belonging to one workspace does not grant access to another. Controllers take the account ID from `TaskOsUserDetails`; services require active membership and the relevant workspace permission before loading or mutating collaboration records.

Existing personal task and project queries include `workspace is null`. Shared queries include the requested workspace ID. Sharing an existing project requires the project owner to review and confirm; the current flow refuses projects with subtasks or tasks already assigned to a workspace. It shares the canonical task rows and project, not copies. Existing project/task activity history and personal goals, habits, focus, time entries, and reminders stay outside the shared view.

## Workspace role policy

| Permission | Owner | Admin | Member | Viewer |
|---|---:|---:|---:|---:|
| View workspace and shared tasks | Yes | Yes | Yes | Yes |
| View task-count workload | Yes | Yes | Yes | Yes |
| Create/change shared tasks, assign, comment | Yes | Yes | Yes | No |
| Edit workspace settings, invite, share owned project | Yes | Yes | No | No |
| Change membership roles, remove members | Yes | Limited | No | No |
| Transfer ownership, archive/restore | Yes | No | No | No |

Admins cannot grant the Admin role, change/remove an Owner, or transfer ownership. Only an Owner can transfer ownership or archive/restore a workspace. The service serializes membership changes on the workspace row and rejects removal of the final active Owner. Archived workspaces are read-only; the Owner can restore them.

## Invitations

The service generates 32 random bytes using `SecureRandom` and returns the URL-safe token once. Only its SHA-256 hash is stored. An invitation expires after seven days, is bound to the normalized invited email, and transitions to accepted, declined, revoked, or expired. Acceptance locks the account, workspace, and invitation; the database also enforces one membership row per user/workspace. Links are delivered by copy/paste in the application; there is no configured email provider or email delivery.

## Task collaboration

Workspace task routes use the canonical `Task` entity with a workspace foreign key. One primary assignment is stored per task; reassignment and removal add assignment history. Removing a member clears their assignments. Comments are limited to 2,000 characters and can be edited/deleted by their author, an Owner, or an Admin. Mention IDs are validated against active workspace memberships; only assignment and direct-mention notifications are delivered in the in-app workspace inbox. Comment bodies render with Thymeleaf `th:text` escaping.

The workspace timeline stores actual workspace, membership, invitation, project-sharing, task-creation, assignment, task-status, and comment events. Each member can independently turn assignment, direct-mention, and activity notifications on or off. Assignment and mention notifications default on; general activity notifications default off to avoid inbox noise. These are in-app only and the timeline is not real time.

Shared project Kanban, Scrum, and Gantt routes enforce `ProjectAccessService` workspace permissions. The shared Kanban lives under `/workspaces/{workspaceId}/projects/{projectId}`; task creation and movement update the canonical project task and synchronize its board placement. Shared Scrum and Gantt use the existing `/projects/{projectId}` routes after resolving the signed-in actor's workspace permission. The team calendar is a read view of shared task due dates; personal calendar blocks and time tracking remain private. Project tasks created by a workspace member retain the project's legacy owner key to satisfy the project-placement foreign key; workspace activity records the actual creating actor.

## Workload and practical limits

Workload counts open assigned tasks and separately counts due-soon, overdue, and high-priority assignments. An optional member-set task-count limit is a capacity signal; it is not an estimate of time or a performance score. Private personal tasks and calendar data are never included. Workspace list/task views show at most 100 rows, comments at most 100, activity 30, and notifications 50; pagination is not wired yet.

## Persistence

Flyway V14 adds `workspaces`, `workspace_memberships`, `workspace_invitations`, `workspace_activity`, `task_assignments`, `task_assignment_history`, `task_comments`, `task_comment_mentions`, and `workspace_notifications`. It adds nullable `workspace_id` scope columns to `projects` and `tasks`; existing rows remain private (`NULL`). Composite foreign keys prevent assigning a task or mention across workspace boundaries. V15 adds per-member assignment, mention, and activity notification preferences. PostgreSQL integration remains opt-in through `TASKOS_TEST_DB_URL`.
