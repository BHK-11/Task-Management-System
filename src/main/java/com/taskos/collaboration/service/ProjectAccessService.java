package com.taskos.collaboration.service;

import com.taskos.collaboration.entity.WorkspaceStatus;
import com.taskos.project.entity.Project;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.task.service.TaskNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAccessService {
    private final ProjectRepository projects;
    private final WorkspaceAuthorizationService authorization;

    public ProjectAccessService(ProjectRepository projects, WorkspaceAuthorizationService authorization) {
        this.projects = projects;
        this.authorization = authorization;
    }

    public Project require(UUID actorId, UUID projectId, WorkspacePermission permission) {
        Project project = projects.findById(projectId).orElseThrow(TaskNotFoundException::new);
        if (project.getWorkspaceId() == null) {
            if (!project.getOwner().getId().equals(actorId)) throw new TaskNotFoundException();
        } else {
            if (permission == WorkspacePermission.EDIT_TASK)
                authorization.requireLocked(actorId, project.getWorkspaceId(), permission);
            else authorization.require(actorId, project.getWorkspaceId(), permission);
        }
        return project;
    }

    @Transactional(readOnly = true)
    public boolean canEdit(UUID actorId, UUID projectId) {
        Project project = projects.findById(projectId).orElseThrow(TaskNotFoundException::new);
        if (project.getWorkspaceId() == null) return project.getOwner().getId().equals(actorId);
        return project.getWorkspace().getStatus() == WorkspaceStatus.ACTIVE
                && authorization.require(actorId, project.getWorkspaceId(), WorkspacePermission.VIEW).getRole()
                        != com.taskos.collaboration.entity.WorkspaceRole.VIEWER;
    }
}
