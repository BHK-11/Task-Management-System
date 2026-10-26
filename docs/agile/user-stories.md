# User stories and acceptance criteria

An Agile story is an existing project `Task` classified as `STORY`. Its task title is the summary and its task description holds the story statement, for example: “As a student, I want to sort tasks by deadline so that I can prioritize upcoming assignments.” The same record remains visible through task, dashboard, project, Kanban, and Scrum views.

`AcceptanceCriterion` records belong to that task and store text, order, and a satisfied flag. The backlog supports add, edit, remove, satisfy/reopen, and keyboard-accessible reorder. A work item can have at most 20 criteria; criterion text is limited to 500 characters.

Satisfied criteria are user-entered supporting evidence. They do not automatically complete a task or represent independent product review. Completing the canonical task through any supported view updates its status and active sprint history.

Project-level Definition of Done guidance is stored as one item per line in `agile_project_settings`. On first backlog display, those defaults are copied to task-specific `DefinitionOfDoneCriterion` rows so each task has independent completion state. A user can add additional checks to an item. Later project-default changes apply to tasks whose checklist has not yet been initialized; existing checklists are not overwritten. These checks are user-confirmed and are not CI evidence. A checked acceptance criterion also cannot prove that tests, code review, or documentation checks ran.
