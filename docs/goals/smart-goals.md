# SMART goals

Create and edit goals at `/goals/new` and `/goals/{id}/edit`. The form stores a title, description, specific outcome, measurement definition, why it matters, feasibility notes, category, progress method, optional baseline/target/unit, start and optional target dates, and an optional project link.

The server validates the title, date order, category, metric, and numeric ranges. Increasing targets must exceed the baseline; decreasing targets must be below it; percent goals use increasing values from 0 through 100. Entering a target date does not complete a goal. Explicit owner-scoped status actions control goal state.

Planning prompts are deterministic: they request a specific outcome, a measurable criterion for numeric goals, and a target date when those fields are absent. They do not score feasibility or claim that an outcome is achievable. The user remains the authority on feasibility.

Numeric creation saves the user-entered baseline as the dated starting measurement. Later check-ins append values with a date and optional note; they do not overwrite history. See [progress calculation](progress-calculation.md).
