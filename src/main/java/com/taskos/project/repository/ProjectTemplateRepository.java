package com.taskos.project.repository;

import com.taskos.project.entity.ProjectTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, UUID> {
    List<ProjectTemplate> findAllByOwner_IdOrderByNameAsc(UUID ownerId);
    Optional<ProjectTemplate> findByIdAndOwner_Id(UUID id, UUID ownerId);
    boolean existsByOwner_IdAndNameIgnoreCase(UUID ownerId, String name);
}
