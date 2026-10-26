# Goal progress calculation

For a numeric goal with baseline `B`, target `T`, and latest dated measurement `C`, the raw fraction is:

```text
(C - B) / (T - B)
```

This signed denominator handles increasing and decreasing goals with the same formula. Values are stored at four decimal places; calculation uses eight decimal places with half-up rounding. A missing measurement or equal baseline and target produces no fraction. A zero value is a real measurement and remains distinct from missing data.

The display percentage clamps the raw fraction to 0–100. The stored measurements are not clamped or rewritten, so over-target values and later retreats remain in history. Milestone progress is completed milestones divided by actual milestones. Task-based progress counts eligible linked canonical top-level tasks, excluding cancelled, archived, and child tasks. Manual-status goals show progress only after the owner marks them complete.

SMART outcome text, a project link, tracked time, or a completed task does not automatically change numerical progress. Only its selected metric's actual records are used.
