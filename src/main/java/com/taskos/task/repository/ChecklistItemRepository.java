package com.taskos.task.repository;

import com.taskos.task.entity.ChecklistItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {
    List<ChecklistItem> findAllByTask_IdOrderByPositionAscCreatedAtAsc(UUID taskId);
    Optional<ChecklistItem> findByIdAndTask_Id(UUID id, UUID taskId);
    long countByTask_Id(UUID taskId);
}
