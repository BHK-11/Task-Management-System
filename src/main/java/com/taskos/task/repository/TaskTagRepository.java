package com.taskos.task.repository;

import com.taskos.task.entity.TaskTag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTagRepository extends JpaRepository<TaskTag, UUID> {
    List<TaskTag> findAllByOwner_IdOrderByNameAsc(UUID ownerId);

    List<TaskTag> findAllByOwner_IdAndIdIn(UUID ownerId, Collection<UUID> ids);

    Optional<TaskTag> findByIdAndOwner_Id(UUID id, UUID ownerId);

    boolean existsByOwner_IdAndName(UUID ownerId, String name);

    long countByOwner_Id(UUID ownerId);
}
