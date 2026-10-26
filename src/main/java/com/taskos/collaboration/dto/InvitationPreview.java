package com.taskos.collaboration.dto;

import com.taskos.collaboration.entity.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public record InvitationPreview(UUID id, UUID workspaceId, String workspaceName, WorkspaceRole role, Instant expiresAt) { }
