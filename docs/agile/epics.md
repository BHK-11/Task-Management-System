# Epics

`Epic` is a project-scoped organizational container. It may have a title, description, optional target date, and archive timestamp. A work item can belong to zero or one epic through `AgileWorkItem.epicId`. The service validates that the task placement and epic belong to the same authorized project; archiving an epic prevents new assignments but keeps its existing associations and history.

Progress excludes missing placements, archived tasks, and cancelled tasks. The epic page displays completed items over eligible items, completed estimated points over estimated points, and the count of unestimated work. It does not assume one point per missing estimate. Zero-point items are included as estimated.

An epic is not a canonical task and cannot itself be completed on the task board. Restored tasks and reopened tasks affect current progress through their canonical status; the computed metric is not a frozen historical snapshot.
