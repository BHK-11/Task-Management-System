package com.taskos.productivity.repository;

import com.taskos.productivity.entity.DailyPlan;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPlanRepository extends JpaRepository<DailyPlan, UUID> {
    Optional<DailyPlan> findByOwner_IdAndPlanningDate(UUID ownerId, LocalDate date);
}
