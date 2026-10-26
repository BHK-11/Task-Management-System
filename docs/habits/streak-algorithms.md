# Schedule-aware streak calculation

The Java `HabitService` builds eligible periods from the effective schedule and habit start date. It then groups saved check-ins into those periods. The current and longest streaks count consecutive successful eligible periods, not raw check-in rows:

- Daily: one successful scheduled date is one streak unit.
- Selected weekdays: one successful selected date is one unit; intervening unscheduled dates are ignored.
- Weekly target: the count target (or quantity total) makes one successful Monday–Sunday period one unit.
- Monthly target: the count target (or quantity total) makes one successful calendar month one unit.

An unfinished current date or period stays open until its period end is before the habit's local current date. It is not a miss, and a current streak may continue through that not-yet-due period. Closed unsuccessful periods break a streak. The longest streak is the maximum run of successful eligible periods in stored history.

Policy: any pause overlap excludes the whole daily, weekly, or monthly period from streak and consistency denominators. A paused day cannot be checked in. Resume ends the pause inclusively on the resume date, so that date remains paused. Paused time is skipped; it is neither success nor failure.

The calculation scans calendar dates from the habit start, then groups its check-ins into periods. It is not constant time; work grows with historical calendar span, schedule changes, check-ins, and pause records. No cached totals are used, so there is no stale streak cache to reconcile.
