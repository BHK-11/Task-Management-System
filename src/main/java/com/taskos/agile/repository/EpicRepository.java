package com.taskos.agile.repository;

import com.taskos.agile.entity.Epic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpicRepository extends JpaRepository<Epic, UUID> {
    List<Epic> findAllByProjectIdAndArchivedAtIsNullOrderByTitleAsc(UUID projectId);
    List<Epic> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<Epic> findByIdAndProjectId(UUID id, UUID projectId);
}
