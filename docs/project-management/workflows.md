# Project and task workflow mapping

Project lifecycle is independent from task status. `ProjectStatus` allows:

- `PLANNED` → `ACTIVE`, `ON_HOLD`, or `CANCELLED`.
- `ACTIVE` → `ON_HOLD`, `COMPLETED`, or `CANCELLED`.
- `ON_HOLD` → `ACTIVE` or `CANCELLED`.
- `COMPLETED` → `ACTIVE`.
- `CANCELLED` → `PLANNED`.

Archiving is a separate timestamped project action. It does not change task state. Archived projects are read-only in the project workspace; restoring re-enables updates.

## Board stage to canonical task status

| Board stage | Task status |
| --- | --- |
| `BACKLOG` | `TODO` |
| `TODO` | `TODO` |
| `IN_PROGRESS` | `IN_PROGRESS` |
| `IN_REVIEW` | `IN_PROGRESS` |
| `ON_HOLD` | `ON_HOLD` |
| `DONE` | `COMPLETED` |
| `CANCELLED` | `CANCELLED` |

The preferred placement for a task status is `TODO`, `IN_PROGRESS`, `ON_HOLD`, `DONE`, or `CANCELLED`, respectively. If a board omits the preferred duplicate stage (`TODO`/`BACKLOG` or `IN_PROGRESS`/`IN_REVIEW`), the remaining compatible column is used. Custom display names do not change the mapping.

Changing project status does not rewrite task status. Existing task tags and Phase 4 methodology classifications remain attached to canonical tasks; board moves only update status, placement, and activity metadata.
