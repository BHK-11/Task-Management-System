package com.taskos.collaboration.service;

import com.taskos.collaboration.dto.ProjectShareReview;
import com.taskos.collaboration.entity.Workspace;
import com.taskos.collaboration.entity.WorkspaceEventType;
import com.taskos.collaboration.repository.WorkspaceRepository;
import com.taskos.project.entity.Project;
import com.taskos.project.entity.ProjectTaskPlacement;
import com.taskos.project.repository.ProjectActivityRepository;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.project.repository.ProjectTaskPlacementRepository;
import com.taskos.task.entity.Task;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkspaceProjectService {
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceRepository workspaces;
    private final ProjectRepository projects;
    private final ProjectTaskPlacementRepository placements;
    private final ProjectActivityRepository projectActivity;
    private final WorkspaceService workspaceService;

    public WorkspaceProjectService(WorkspaceAuthorizationService authorization, WorkspaceRepository workspaces,
            ProjectRepository projects, ProjectTaskPlacementRepository placements,
            ProjectActivityRepository projectActivity, WorkspaceService workspaceService) {
        this.authorization = authorization; this.workspaces = workspaces; this.projects = projects;
        this.placements = placements; this.projectActivity = projectActivity;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public List<Project> sharedProjects(UUID actorId, UUID workspaceId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        return projects.findAllByWorkspace_IdAndArchivedAtIsNullOrderByUpdatedAtDesc(workspaceId);
    }

    @Transactional(readOnly = true)
    public Project sharedProject(UUID actorId, UUID workspaceId, UUID projectId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        return projects.findByIdAndWorkspace_Id(projectId, workspaceId).orElseThrow(TaskNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<Project> shareableProjects(UUID actorId, UUID workspaceId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.SHARE_PROJECT);
        return projects.findAllByOwner_IdAndArchivedAtIsNullOrderByUpdatedAtDesc(actorId);
    }

    @Transactional(readOnly = true)
    public List<ProjectTaskPlacement> sharedTasks(UUID actorId, UUID workspaceId, UUID projectId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.VIEW);
        Project project = projects.findByIdAndWorkspace_Id(projectId, workspaceId).orElseThrow(TaskNotFoundException::new);
        return placements.findAllSharedForProject(project.getId(), workspaceId);
    }

    @Transactional(readOnly = true)
    public ProjectShareReview review(UUID actorId, UUID workspaceId, UUID projectId) {
        authorization.require(actorId, workspaceId, WorkspacePermission.SHARE_PROJECT);
        Project project = projects.findByIdAndOwner_Id(projectId, actorId).orElseThrow(TaskNotFoundException::new);
        List<ProjectTaskPlacement> linkedTasks = placements.findAllForProject(actorId, projectId);
        String restriction = linkedTasks.stream().anyMatch(placement -> placement.getTask().getParentId() != null)
                ? "Projects containing subtasks cannot be shared yet; keep the existing project private."
                : linkedTasks.stream().anyMatch(placement -> placement.getTask().getWorkspaceId() != null)
                        ? "A linked task already belongs to another workspace."
                        : "";
        return new ProjectShareReview(project, linkedTasks, projectActivity.countByOwnerIdAndProjectId(actorId, projectId),
                restriction.isEmpty(), restriction);
    }

    public void share(UUID actorId, UUID workspaceId, UUID projectId, boolean confirmed) {
        if (!confirmed) throw new TaskRuleException("Confirm the project sharing review before continuing.");
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.SHARE_PROJECT);
        Workspace workspace = workspaces.findLockedById(workspaceId).orElseThrow(TaskNotFoundException::new);
        Project project = projects.findLockedByIdAndOwner(projectId, actorId).orElseThrow(TaskNotFoundException::new);
        if (project.getArchivedAt() != null) throw new TaskRuleException("Restore the project before sharing it.");
        List<ProjectTaskPlacement> linkedTasks = placements.findAllForProject(actorId, projectId);
        if (linkedTasks.stream().anyMatch(placement -> placement.getTask().getParentId() != null
                || placement.getTask().getWorkspaceId() != null))
            throw new TaskRuleException("This project contains tasks that cannot be shared safely.");
        Instant now = Instant.now();
        linkedTasks.forEach(placement -> placement.getTask().shareWith(workspace));
        project.shareWith(workspace, now);
        workspaceService.record(workspaceId, actorId, WorkspaceEventType.PROJECT_SHARED,
                "Project “" + project.getName() + "” shared with the workspace", now);
    }
}
