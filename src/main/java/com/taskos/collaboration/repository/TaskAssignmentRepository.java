package com.taskos.collaboration.repository;

import com.taskos.collaboration.entity.TaskAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, UUID> {
    Optional<TaskAssignment> findByTaskIdAndWorkspaceId(UUID taskId, UUID workspaceId);
    List<TaskAssignment> findAllByWorkspaceIdAndAssigneeIdIn(UUID workspaceId, List<UUID> assigneeIds);
    List<TaskAssignment> findAllByWorkspaceIdAndAssigneeIdOrderByAssignedAtDesc(UUID workspaceId, UUID assigneeId);
    List<TaskAssignment> findAllByWorkspaceIdAndAssigneeId(UUID workspaceId, UUID assigneeId);
    List<TaskAssignment> findAllByAssigneeIdOrderByAssignedAtDesc(UUID assigneeId);
}
