package com.taskos.collaboration.repository;

import com.taskos.collaboration.entity.WorkspaceActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceActivityRepository extends JpaRepository<WorkspaceActivity, UUID> {
    List<WorkspaceActivity> findAllByWorkspaceIdOrderByOccurredAtDesc(UUID workspaceId, Pageable pageable);
}
