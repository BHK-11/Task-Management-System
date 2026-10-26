package com.taskos.project.repository;

import com.taskos.project.entity.ProjectActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectActivityRepository extends JpaRepository<ProjectActivity, UUID> {
    List<ProjectActivity> findAllByOwnerIdAndProjectIdOrderByOccurredAtDesc(UUID ownerId, UUID projectId, Pageable pageable);
    long countByOwnerIdAndProjectId(UUID ownerId, UUID projectId);
}
