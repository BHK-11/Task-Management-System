# Habit and goal timezone policy

Habit start dates, check-in dates, schedules, period boundaries, current-day cutoffs, and streaks use the timezone saved on the habit. Changing a user's account timezone later does not reinterpret existing habit dates. Check-ins cannot be in the future or before the habit start date.

Habit check-ins use `LocalDate`, so daylight-saving gaps and overlaps do not create or remove a habit day. Reminder timestamps are resolved by the existing time/reminder service using the habit timezone. Goal and key-result check-in date defaults and future-date validation use the account timezone through `TimeZoneResolver`.

Weekly windows are Monday through Sunday in the habit's calendar. Monthly windows use the calendar month. Effective weekly changes must use Monday; monthly changes must use the first day of a month. Pause start/end dates are inclusive; resuming today keeps today inside the pause.
