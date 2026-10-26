# UI foundation

## Existing frontend

TASK OS currently uses Spring MVC, Thymeleaf, vanilla JavaScript, and application CSS. `package.json` contains Motion and a GSAP development dependency; the repository does not contain a React/Next.js, TypeScript, Tailwind, shadcn/ui, or Radix application. The existing theme and component conventions remain authoritative; this phase adds no UI framework dependency.

Semantic colors, surfaces, typography, shadows, and corner radii live in `src/main/resources/static/css/app.css`. The current navy/blue palette and its dark-theme overrides are retained. Account theme preference is managed on the profile page; the browser-side simple/advanced preference is stored as `taskos-experience-mode` because it changes presentation only.

## Application shell

- Desktop widths above 1020px keep the fixed sidebar and top toolbar.
- Tablet widths from 761px through 1020px use the accessible off-canvas sidebar.
- Phone widths at or below 760px add a five-destination bottom bar: Home, Tasks, Ask AI, Calendar, and More. More opens the existing full navigation drawer.
- The drawer and bottom bar respect safe-area insets; workspace content reserves space for the fixed bar.
- Simple mode hides navigation links marked advanced. It does not alter route authorization or backend permissions. The current advanced destination remains available in the sidebar.

The native command dialog opens from the toolbar or `Ctrl+K` / `Cmd+K`. It filters navigation commands and sends task/project queries to the existing `/tasks?q=…` and `/projects?q=…` pages, which continue to apply their server-side access rules. It does not load records into browser storage.

The `/tasks` page is the task execution workspace: filtered results stay paginated, selecting a task keeps the current query state, and the detail panel shows status, progress, notes, subtasks, focus actions, and project dependencies. Previous/next selection crosses page boundaries while retaining filters. On narrow screens the selected detail expands under its task row. Subtask completion uses the owner-scoped `TaskService` and rolls back the checkbox if the request fails. Attachments have no storage model or upload route yet; project scheduling is the only current task dependency source.

## Accessibility and limits

The shell provides a skip link, visible focus styles, labelled navigation, current-route state, native modal semantics, focus restoration, Escape dismissal, and reduced-motion handling. New mobile and search icons are inline SVG; the older sidebar still uses its existing Unicode glyphs. This pass does not convert every feature page to a new component library.

Verification covers Thymeleaf rendering, static JS/CSS delivery, application integration tests, and JavaScript syntax. No automated browser viewport, screen-reader, contrast-measurement, or visual-regression harness is configured in this repository, so those checks remain outstanding.
