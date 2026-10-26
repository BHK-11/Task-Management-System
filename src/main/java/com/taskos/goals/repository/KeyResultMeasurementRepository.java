package com.taskos.goals.repository;

import com.taskos.goals.entity.KeyResultMeasurement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyResultMeasurementRepository extends JpaRepository<KeyResultMeasurement, UUID> {
    List<KeyResultMeasurement> findAllByOwnerIdAndKeyResultIdOrderByMeasurementDateDescRecordedAtDesc(UUID ownerId, UUID keyResultId);
    Optional<KeyResultMeasurement> findFirstByOwnerIdAndKeyResultIdOrderByMeasurementDateDescRecordedAtDesc(UUID ownerId, UUID keyResultId);
    long countByOwnerIdAndMeasurementDateBetween(UUID ownerId, java.time.LocalDate start, java.time.LocalDate end);
}
