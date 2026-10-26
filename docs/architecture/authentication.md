# TASK OS authentication and identity flow

## Request flow

1. The browser submits the Thymeleaf login form to `POST /login` with a CSRF token, email, and password.
2. Spring Security's `UsernamePasswordAuthenticationFilter` passes credentials to the configured `AuthenticationManager` and provider.
3. `DatabaseUserDetailsService` canonicalizes the email and loads a `UserAccount` with its role assignments through `UserAccountRepository`.
4. Spring Security's `PasswordEncoder` verifies the submitted password against the stored delegating PBKDF2 hash. The application does not compare raw passwords itself.
5. If the account is active and the password matches, Spring Security stores the authenticated principal and authorities in a server-managed HTTP session. Session fixation protection changes the session identifier.
6. `LoginSuccessHandler` records the login timestamp and audit event, clears that email/network pair's failure counter, and redirects to `/dashboard`. The legacy `/app` route redirects to the dashboard.
7. For invalid credentials or disabled accounts, `LoginFailureHandler` records a generic failure event and redirects to `/login?error`. The UI uses one generic message.

Public registration is handled by `AuthenticationController` and `RegistrationService`. Bean Validation checks the form; the service normalizes the name and email, encodes the password, and atomically saves the user, the seeded `ROLE_USER` assignment, and the audit event. A database unique constraint handles concurrent duplicate addresses.

## Authorization boundaries

`SecurityConfiguration` permits public product pages, signup/login, static assets, safe error routes, and restricted Actuator status. It requires authentication for all other application routes and requires `ROLE_ADMIN` for `/admin/**`. The seeded `ROLE_ADMIN` row does not create an administrator account, and signup forms have no role field.

Account handlers read the account UUID only from `TaskOsUserDetails`. Task and tag controllers do the same, and task mutations load records by both submitted resource ID and authenticated owner ID. Bulk operations validate every selected record belongs to that owner before applying changes. The browser cannot select another account by changing a URL, form, or tag ID. Workspace roles are separate from global `ROLE_USER` / `ROLE_ADMIN`; services re-check active membership and role for each workspace operation. See [collaboration.md](collaboration.md).

## Passwords and sessions

`SecurityConfiguration` uses `DelegatingPasswordEncoder` with the Spring Security PBKDF2 configuration as the current encoding. Stored values include an algorithm identifier so a future encoder can be introduced and old hashes migrated on successful authentication. Passwords are required to be 12–256 characters; no character-class rules are imposed.

CSRF is enabled for state-changing browser requests, including logout. Sessions rotate on authentication, expire after 30 minutes of inactivity, and are limited to three active sessions per account. Cookies are HttpOnly and SameSite=Lax; the production profile sets Secure and is intended for HTTPS. A password change checks the existing hash, writes the new hash, expires the account's tracked sessions, and clears the current session.

The login throttle is process-local: ten failed logins for the same canonical email and remote network address in 15 minutes produce HTTP 429. An ephemeral random HMAC key keeps the in-memory map from holding plain email/IP pairs. This is suitable for the current single-process foundation; a deployment with multiple app instances should add shared edge or datastore-backed throttling before scaling out. Proxy headers are not used as client identity.

## Persistence

`V2__create_user_identity.sql` adds:

- `task_os.users`: canonical email, password hash, account status, theme/timezone/locale preferences, and timestamps.
- `task_os.roles`: global `ROLE_USER` and `ROLE_ADMIN` authority names.
- `task_os.user_roles`: the many-to-many user-role relation with foreign keys.
- `task_os.auth_events`: limited event types and timestamps, with no raw email, IP, password, or session secret.

Audit events are retained for 90 days and purged daily. Failed-login events have a null `user_id` so the event does not reveal whether the submitted address has an account. The audit stream is operational metadata, not an intrusion-detection service.

`V3__create_task_management.sql` adds private task and tag ownership, subtasks, checklist items, and activity records. Task activity is scoped to its owner and survives hard deletion of the referenced task; account deletion still cascades through the account's data. See [tasks.md](tasks.md) for task-specific rules.

## Profile and preference rules

`/profile` edits the display name for the authenticated account and presents the email as read-only. Changing an email would change the login identity, so it is deferred until a real email verification provider is configured. Account preferences currently store a controlled theme, one of five supported timezones, and English. Password recovery, email verification, remember-me, avatar upload, and self-service admin assignment are not implemented.
