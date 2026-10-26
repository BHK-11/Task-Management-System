# Calendar, focus, and time tracking

Phase 8 adds a database-backed personal schedule around the existing canonical task, project, milestone, and sprint records. Flyway migration `V12` stores reservations, focus sessions and intervals, time entries and segments, recurrence rules and occurrences, reminders, and in-app notifications.

## Calendar and time blocks

`/calendar` supports month, week, and day views. Its `view` and `date` query parameters preserve navigation state in shareable URLs. It shows task due dates, project milestones, and the owner's planned time blocks. Calendar range reads are bounded to the visible period; task selection is capped at 100 options.

Time blocks are owner-scoped, versioned records tied optionally to a canonical task. An overlap is reported to the user as a warning and both blocks remain scheduled. Blocks are limited to 16 hours. The calendar's drag interaction moves a block by preserving its local wall time and duration; the server performs the actual validation and save.

Local date/time fields resolve with the user's saved timezone. Java's `LocalDateTime.atZone` policy moves nonexistent daylight-saving wall times forward through the gap and chooses the earlier offset for repeated wall times. Invalid saved zones fall back to UTC. Database timestamps remain instants.

## Focus and tracking

Pomodoro sessions support configurable focus, short-break, and long-break lengths. A long break follows every fourth completed focus interval. Deep Work uses the same persistent timer and focus history with a single longer interval. Session state and phase intervals are stored in Java services; the browser only displays the server-provided countdown and submits phase completion, which the server checks against its clock. Stopping a session records completed focus intervals as a linked time entry when there is focus time to save.

Work tracking stores a running entry and separate active segments, so pause/resume excludes idle time. A user can have only one running or paused work timer at a time. Manual time entries can be created, edited, and deleted. Overlapping work is allowed with a warning; total tracked time merges overlapping segments, while task totals are attributed by task and can therefore exceed the exclusive total if different tasks overlap.

## Recurrence, reminders, and reports

Recurrence rules create new canonical tasks from a chosen task template. Daily, weekly, monthly, and yearly schedules support intervals, ISO weekdays, an end date or occurrence limit, and `SKIP` or `LAST_DAY` month-end behavior. The scheduler processes due rules in bounded batches, records each occurrence uniquely, and catches up at most five occurrences per rule on each pass. Users can pause, resume, end, skip, or reschedule the next occurrence. Editing a generated task changes only that occurrence; editing its template changes the content copied into future occurrences, leaving already generated tasks intact.

Reminders are delivered into the in-app inbox by the Java scheduler and can be snoozed or dismissed. No email, SMS, browser push, or operating-system notification provider is configured. Browser notifications are optional and are not implemented.

The basic insights page reports tracked time by local day and task, plus planned reservations. Project task time is labeled with its current project using the canonical task placement. Planned hours are gross reservations; overlapping plans count more than once. Actual tracked hours are exclusive, so the displayed difference compares two different measures and is descriptive rather than a compliance score. The main dashboard adds a compact count of today's deadlines, milestones, and time blocks without changing existing task metrics.

## Boundaries

Calendar and task routes use the authenticated owner ID for every read and write. Mutating forms use CSRF protection. Timer actions serialize on the account row, and editable blocks/manual entries use optimistic versions. No activity is fabricated: timers, entries, occurrences, and notifications are persisted records.

The test profile validates migrations and MVC/service behavior on H2. The dedicated PostgreSQL integration test remains opt-in through `TASKOS_TEST_DB_URL`; the H2 suite does not verify PostgreSQL locking or deployment scheduler behavior. The app does not block device interruptions, prove attention, level work by resource availability, or provide external reminder delivery.
