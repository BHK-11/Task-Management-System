# Java OOP concepts in TASK OS

This guide points to implemented application code, including collaboration. It avoids adding artificial inheritance or example-only classes.

| Concept | Implemented example | What it demonstrates |
|---|---|---|
| Encapsulation | `user/entity/UserAccount.java` | Persistence fields are private; registration and account changes go through focused methods. There are no public setters for role, password hash, ID, or timestamps. |
| Abstraction | `security/service/DatabaseUserDetailsService.java` implements Spring Security's `UserDetailsService`; repositories expose typed persistence operations. | Spring Security authenticates through a stable contract while the database lookup remains behind the implementation. |
| Polymorphism | `TaskOsUserDetails` implements `UserDetails`; success/failure/logout handlers implement Spring Security handler contracts. | Framework code invokes the configured implementation through its interface. |
| Inheritance | No custom domain inheritance hierarchy. | The identity model has no natural “is-a” extension that needs inheritance. |
| Interfaces | `UserDetailsService`, `AuthenticationSuccessHandler`, `AuthenticationFailureHandler`, and Spring Data `JpaRepository`. | Framework interfaces define replaceable application boundaries. |
| Collections | `UserAccount.roles` is a `LinkedHashSet<Role>` and the security principal holds an immutable authority list. | An account can have a set of global roles without duplicates; authorities are safely exposed to Spring Security. |
| Exception handling | `RegistrationService.DuplicateEmailException`, `UserProfileService.InvalidCurrentPasswordException`, and `GlobalExceptionHandler`. | Duplicate accounts and invalid credentials become safe outcomes without exposing database or password details. |
| Dependency injection | Constructor parameters in controllers, services, handlers, and `SecurityConfiguration`. | Spring provides repositories, encoders, session registry, and collaborators instead of classes constructing them directly. |
| MVC | `AuthenticationController`, `AccountController`, `AuthenticatedModelAdvice`, and Thymeleaf templates. | Request routing, validation, model preparation, and page rendering have distinct jobs. |
| Database connectivity | `UserAccountRepository`, `RoleRepository`, `AuthEventRepository`, JPA entities, and Flyway V2. | PostgreSQL persistence is versioned and mapped through Spring Data JPA. |
| Transactions | `RegistrationService.register`, profile updates, password changes, and audit writes. | Related identity changes commit or roll back together. |
| SOLID responsibilities | Registration, profile management, user lookup, auditing, handlers, and security configuration are separate classes. | Each part has a narrow responsibility; Spring Security contracts support dependency inversion. |
| Domain behavior | `task/entity/Task.java`, `task/service/TaskService.java`, and `task/service/ChecklistService.java` | Task status transitions, ownership checks, checklist changes, and activity recording stay in the domain/service layer rather than being duplicated in templates. |
| Project encapsulation | `project/entity/Project.java`, `BoardColumn.java`, `ProjectMilestone.java` | Project lifecycle, column changes, and milestone transitions are controlled by domain methods and validated by the service. |
| Workflow enums | `project/entity/ProjectStatus.java`, `BoardStage.java`, `WipPolicy.java`, `MilestoneStatus.java` | Project and board states are typed; `BoardStage` maps columns to canonical task statuses. |
| Project service boundary | `project/service/ProjectService.java` and typed Spring Data repositories | Owner checks, transactions, strict WIP validation, position changes, progress and template application stay in Java. |
| Project-task association | `project/entity/ProjectTaskPlacement.java` | A placement uses the canonical task ID and stores only project, column, milestone, and order metadata. There is no competing task entity. |
| Typed template data | `project/dto/ProjectTemplateDefinition.java` | Versioned records define columns, milestones, and optional task examples without Java object serialization. |
| Task synchronization | `task/event/TaskStatusChangedEvent.java` and the `ProjectService` listener | A successful status change updates the board placement synchronously in the same transaction. |
| Agile metadata composition | `agile/entity/AgileWorkItem.java` keyed by canonical `task_id` | Scrum types, story points, epic links, Definition of Done guidance and backlog order extend existing tasks without a duplicate task hierarchy. |
| Sprint lifecycle | `agile/entity/Sprint.java`, `SprintMembership.java`, and `AgileService.java` | Typed state transitions, project-locked active-sprint checks, baseline snapshots, scope auditing and close-time delivery metrics stay in Java. |
| Event-reconstructed metric | `agile/entity/SprintEvent.java` and `AgileService.burndown` | A captured start baseline plus timestamped changes reconstructs daily actual remaining work; ideal points are a calendar reference. |
| Definition of Done checklist | `agile/entity/DefinitionOfDoneCriterion.java` | Project guidance seeds task-specific checklist rows; each item's completion state stays independently persisted and user-confirmed. |
| Retrospective follow-through | `agile/entity/SprintActionItem.java` and `AgileService.convertRetrospectiveAction` | A retrospective action keeps its sprint reference and links idempotently to a canonical task created by `ProjectService`. |
| Existing module integration | `agile/controller/AgileController.java`, `TaskService`, and `ProjectService` | MVC uses owner-scoped services; task status and Kanban movement reuse the existing canonical services and workflow rules. |
| Goal encapsulation | `goals/entity/Goal.java`, `GoalMilestone.java`, and `GoalService.java` | Typed lifecycle transitions and milestone edits stay behind domain/service methods; progress is derived from actual saved measurements and linked canonical work. |
| Progress abstraction | `goals/service/ProgressCalculator.java` | One signed baseline-to-target formula supports increasing and decreasing measures; display clamping is separate from historical values. |
| OKR composition | `goals/entity/OkrCycle.java`, `Objective.java`, `KeyResult.java`, and `OkrService.java` | Cycles own objectives; objectives own quantitative results; only measured result fractions contribute to the aggregate. |
| Habit period algorithm | `habits/service/HabitService.java`, `HabitFrequency.java`, `HabitSchedule.java` | Typed schedule policies create daily, selected-weekday, weekly, and monthly periods; `LocalDate` and `ZoneId` define due periods and streaks. |
| Review persistence | `goals/entity/Review.java`, `ReviewService.java`, and `ReviewRepository.java` | Owner and period uniqueness preserve drafts and completed reviews without replacing prior periods. |
| Workspace composition | `collaboration/entity/Workspace.java` and `WorkspaceMembership.java` | A workspace composes member links and shared resource references; memberships carry workspace roles instead of changing global account roles. |
| Authorization policy | `collaboration/service/WorkspaceAuthorizationService.java` and `WorkspacePermission.java` | One Java policy maps explicit operations to workspace roles and checks active membership for each service request. |
| Transactional membership changes | `collaboration/service/WorkspaceService.java` | Workspace row locks serialize owner transfer, member removal, role changes, and invitations; service methods retain the final-owner invariant. |
| Capability token handling | `collaboration/service/WorkspaceInvitationService.java` | A cryptographically random bearer token is returned once, SHA-256 hashed for persistence, recipient-bound, expires after seven days, and is consumed once. |
| Member notification preferences | `collaboration/entity/WorkspaceMembership.java` and `WorkspaceService.java` | Assignment, mention, and activity delivery settings are stored on the member's workspace membership, not as global account state. |
| Shared canonical tasks | `collaboration/service/WorkspaceTaskService.java`, `TaskAssignment.java`, and `TaskComment.java` | A workspace points to the existing `Task`; assignment, history, comments, and mentions compose around its canonical task ID. |
| Explicit data scope | `task/repository/TaskRepository.java`, `TaskSpecifications.java`, and `project/repository/ProjectRepository.java` | Personal queries require a null workspace scope, while collaboration queries include the requested workspace ID. |

Passwords are never copied into presentation DTOs. `ProfileView` contains display-safe fields only; authentication hashes are used only by the security service and password-change service.
