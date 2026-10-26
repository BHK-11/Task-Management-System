package com.taskos.agile.repository;

import com.taskos.agile.entity.SprintEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface SprintEventRepository extends JpaRepository<SprintEvent, UUID> {
    List<SprintEvent> findAllByProjectIdOrderByOccurredAtDesc(UUID projectId);
    List<SprintEvent> findByProjectIdOrderByOccurredAtDesc(UUID projectId, Pageable pageable);
    List<SprintEvent> findAllBySprintIdOrderByOccurredAtAsc(UUID sprintId);
}
