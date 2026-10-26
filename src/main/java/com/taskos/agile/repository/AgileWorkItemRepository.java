package com.taskos.agile.repository;

import com.taskos.agile.entity.AgileWorkItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgileWorkItemRepository extends JpaRepository<AgileWorkItem, UUID> {
    List<AgileWorkItem> findAllByProjectIdOrderByBacklogPositionAsc(UUID projectId);
    List<AgileWorkItem> findAllByProjectIdAndEpicId(UUID projectId, UUID epicId);
    boolean existsByProjectId(UUID projectId);
    boolean existsByTaskId(UUID taskId);
    boolean existsByTaskIdIn(Collection<UUID> taskIds);
}
