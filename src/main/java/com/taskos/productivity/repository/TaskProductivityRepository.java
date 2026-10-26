package com.taskos.productivity.repository;

import com.taskos.productivity.entity.TaskProductivity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskProductivityRepository extends JpaRepository<TaskProductivity, UUID> {
    Optional<TaskProductivity> findByIdAndOwner_Id(UUID taskId, UUID ownerId);
    List<TaskProductivity> findAllByOwner_IdAndIdIn(UUID ownerId, Collection<UUID> taskIds);
    List<TaskProductivity> findAllByOwner_Id(UUID ownerId);
}
