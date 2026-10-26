package com.taskos.scheduling.repository;

import com.taskos.scheduling.entity.ScheduleBaseline;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleBaselineRepository extends JpaRepository<ScheduleBaseline, UUID> {
    List<ScheduleBaseline> findAllByOwnerIdAndProjectIdOrderByCreatedAtDesc(UUID ownerId, UUID projectId);
    Optional<ScheduleBaseline> findByIdAndOwnerIdAndProjectId(UUID id, UUID ownerId, UUID projectId);
}
