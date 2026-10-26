package com.taskos.scheduling.repository;

import com.taskos.scheduling.entity.TaskScheduleMetadata;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskScheduleMetadataRepository extends JpaRepository<TaskScheduleMetadata, UUID> {
    List<TaskScheduleMetadata> findAllByOwnerIdAndTaskIdIn(UUID ownerId, Collection<UUID> taskIds);
    Optional<TaskScheduleMetadata> findByTaskIdAndOwnerId(UUID taskId, UUID ownerId);
}
