package com.taskos.project.repository;

import com.taskos.project.entity.BoardColumn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, UUID> {
    List<BoardColumn> findAllByOwnerIdAndProjectIdOrderByPositionAsc(UUID ownerId, UUID projectId);
    Optional<BoardColumn> findByIdAndOwnerIdAndProjectId(UUID id, UUID ownerId, UUID projectId);
    boolean existsByOwnerIdAndProjectIdAndNameIgnoreCase(UUID ownerId, UUID projectId, String name);
}
