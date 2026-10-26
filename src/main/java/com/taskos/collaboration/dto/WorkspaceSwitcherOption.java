package com.taskos.collaboration.dto;

import com.taskos.collaboration.entity.WorkspaceRole;
import java.util.UUID;

public record WorkspaceSwitcherOption(UUID id, String name, WorkspaceRole role) { }
