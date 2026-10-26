package com.taskos.task.repository;

import com.taskos.task.entity.TaskActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, UUID> {
    List<TaskActivity> findAllByOwner_IdAndTaskIdOrderByOccurredAtDesc(UUID ownerId, UUID taskId, Pageable pageable);

    List<TaskActivity> findAllByOwner_IdOrderByOccurredAtDesc(UUID ownerId, Pageable pageable);
}
