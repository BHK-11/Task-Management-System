# Habit schedules and check-ins

The `Habit` record stores the owner, start date, timezone, optional goal/task references, and lifecycle state. One or more `HabitSchedule` records define the effective-dated frequency; `HabitCheckIn` stores one editable record per habit and local date; `HabitPause` stores inclusive paused date ranges. Check-ins are persisted in Java-backed services and are not inferred from task completion or planned time blocks.

Supported schedules are daily, selected ISO weekdays (Monday = 1 through Sunday = 7), a number of completions per Monday–Sunday week, and a number per calendar month. A quantity target is checked per scheduled day for daily/selected-day schedules and summed across the weekly/monthly window for count-based schedules. A check-in may be revised for the same date; the unique constraint prevents duplicates.

Schedule changes take effect on a future date and do not rewrite old schedule rows. Weekly changes must start Monday; monthly changes must start on day 1. Pause and resume preserve history. Archived habits cannot receive new check-ins. See [streak calculation](streak-algorithms.md) and [timezone policy](timezone-policy.md).

The calendar displays actual check-ins, missed past scheduled days, open current periods, unscheduled dates, pause dates, and upcoming dates with text labels as well as colors. No check-ins are seeded automatically.
