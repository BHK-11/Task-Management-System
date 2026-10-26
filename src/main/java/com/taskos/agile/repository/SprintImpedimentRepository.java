package com.taskos.agile.repository;

import com.taskos.agile.entity.SprintImpediment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintImpedimentRepository extends JpaRepository<SprintImpediment, UUID> {
    List<SprintImpediment> findAllBySprintIdOrderByReportedAtDesc(UUID sprintId);
    Optional<SprintImpediment> findByIdAndSprintId(UUID id, UUID sprintId);
}
