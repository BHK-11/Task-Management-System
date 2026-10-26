package com.taskos.goals.repository;

import com.taskos.goals.entity.GoalMeasurement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalMeasurementRepository extends JpaRepository<GoalMeasurement, UUID> {
    List<GoalMeasurement> findAllByOwnerIdAndGoalIdOrderByMeasurementDateDescRecordedAtDesc(UUID ownerId, UUID goalId);
    List<GoalMeasurement> findAllByOwnerIdAndGoalIdInOrderByMeasurementDateDescRecordedAtDesc(UUID ownerId, Collection<UUID> goalIds);
    Optional<GoalMeasurement> findFirstByOwnerIdAndGoalIdOrderByMeasurementDateDescRecordedAtDesc(UUID ownerId, UUID goalId);
    long countByOwnerIdAndMeasurementDateBetween(UUID ownerId, java.time.LocalDate from, java.time.LocalDate through);
}
