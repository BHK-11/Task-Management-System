# Goal and progress architecture

Goals live in `com.taskos.goals`. `GoalController` handles authenticated MVC routes; `GoalService` validates ownership, status, project links, milestones, task links, and measurements; repositories persist the domain records. Thymeleaf views are under `templates/goals`.

`Goal` stores the desired outcome and selected metric. `GoalMeasurement` stores dated evidence, `GoalMilestone` stores the ordered roadmap, and `GoalTaskLink` references the existing task by ID. Goals may also reference a user's existing project. Neither link creates a copy of task or project data.

The authenticated principal supplies the owner ID. Service lookups include that owner; database composite foreign keys also pair `owner_id` with the goal and task IDs for measurement and task-link rows. Goal archiving leaves linked tasks and project data intact. Archived goals are hidden from the active list while their history remains in storage.

The V13 migration is forward-only. H2 tests exercise it in PostgreSQL compatibility mode; the dedicated PostgreSQL test remains opt-in and must be reported separately. See [SMART goal behavior](smart-goals.md), [progress formulas](progress-calculation.md), and [task links](task-linking.md).
