package com.taskos.agile.repository;

import com.taskos.agile.entity.SprintBaselineItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintBaselineItemRepository extends JpaRepository<SprintBaselineItem, UUID> {
    List<SprintBaselineItem> findAllBySprintId(UUID sprintId);
}
