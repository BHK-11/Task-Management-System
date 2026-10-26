package com.taskos.agile.repository;

import com.taskos.agile.entity.SprintActionItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintActionItemRepository extends JpaRepository<SprintActionItem, UUID> {
    List<SprintActionItem> findAllBySprintIdOrderByPositionAsc(UUID sprintId);
    Optional<SprintActionItem> findByIdAndSprintId(UUID id, UUID sprintId);
    long countBySprintId(UUID sprintId);
}
