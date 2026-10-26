package com.taskos.goals.repository;

import com.taskos.goals.entity.OkrCycle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OkrCycleRepository extends JpaRepository<OkrCycle, UUID> {
    List<OkrCycle> findAllByOwnerIdOrderByStartDateDesc(UUID ownerId);
    Optional<OkrCycle> findByOwnerIdAndNameAndStartDateAndEndDate(UUID ownerId, String name, LocalDate start, LocalDate end);
    Optional<OkrCycle> findByIdAndOwnerId(UUID id, UUID ownerId);
}
