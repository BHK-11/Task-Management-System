# Goal links to tasks and projects

`GoalTaskLink` stores owner, goal, and canonical task IDs. The task remains the same record used by the task list, project board, and Scrum views. The database rejects duplicate goal/task links and cross-owner links. Removing a link removes only the association; archiving a goal preserves the task.

Task-completion progress is available when selected as the goal metric. It uses linked, top-level tasks that are not cancelled or archived. A reopened task no longer contributes as completed. Child-task status, time spent, story points, and unrelated task completion are not converted to goal progress.

Each goal can optionally reference one of the owner's existing projects. Project progress is displayed as context and does not substitute for an outcome measurement. See [goal architecture](architecture.md).
