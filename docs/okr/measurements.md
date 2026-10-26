# Key result measurements

Each check-in is a `KeyResultMeasurement` with the owner, key-result ID, actual value, measurement date, record time, and optional note. Updates append history. The newest dated value is used as the current reading; same-day ties use the latest recorded time.

Each measured result uses the same signed fraction as a goal: `(current - baseline) / (target - baseline)`. Objective progress is the equal-weight mean of measured key-result fractions only. Unmeasured results are excluded, not treated as zero. If no key result has a measurement, aggregate progress is absent. Display percentages clamp to 0–100 while the recorded values and raw fractions remain unchanged.

Measurements dated after the owner's current local date are rejected. Owner-scoped service lookups prevent reading or updating another account's objective or key result. H2 tests cover these rules; PostgreSQL behavior requires the opt-in database test.
