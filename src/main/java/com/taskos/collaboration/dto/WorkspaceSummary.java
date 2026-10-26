package com.taskos.collaboration.dto;

import com.taskos.collaboration.entity.WorkspaceRole;
import java.util.UUID;

public record WorkspaceSummary(UUID id, String name, String description, WorkspaceRole role,
        long members, long projects, long openTasks) { }
