# Project templates

Templates are owner-scoped records with a schema version and a typed `ProjectTemplateDefinition` JSON payload. The current version stores ordered workflow columns, milestone blueprints, and optional task examples. The library shows a preview before a template is used.

Applying a template creates the project and workflow inside one transaction. Milestones and optional examples are created only after validating the stored definition. Task examples call the existing `TaskService.create`, receive new UUIDs, and are placed against their newly created canonical task rows. A failure rolls back the project, columns, milestones, activity, and tasks together.

Task examples are opt-in when saving a template. They copy title, description, priority, destination column, optional milestone link, and an optional due-date offset from the source project start date. Date offsets outside ten years are discarded or rejected. Existing tags, subtasks, checklists, and Phase 4 methodology decisions are not copied; this avoids transferring personal classifications implicitly.

The service accepts schema version 1 only and validates column, milestone, task, position, and date bounds before use. A future incompatible definition needs a new version and explicit migration logic. Deleting a template does not change projects previously created from it.
