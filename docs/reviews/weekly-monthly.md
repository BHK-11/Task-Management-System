# Weekly and monthly reviews

`ReviewService` creates an owner-scoped draft for the selected Monday–Sunday week or calendar month. Saving a draft preserves the user-authored wins, challenges, next steps, and reflection; choosing Complete records completion. The owner/period unique constraint retains one review per period, and a new period does not overwrite an older one.

The workspace reports actual completed-task counts, performed habit check-ins, goal and key-result measurements, and completed time-entry duration within the local period. It labels those facts as counts or duration and does not infer causes, missed commitments, objective outcomes, or a universal productivity score. Reflection text is user supplied. `/reviews/history` links to saved periods.

Review pages are available at `/reviews/weekly`, `/reviews/monthly`, `/reviews/history`, and `/reviews/{id}`. Period boundaries use the account timezone for timestamp-based task and time-entry data.
