package com.taskos.habits.repository;

import com.taskos.habits.entity.HabitCheckIn;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitCheckInRepository extends JpaRepository<HabitCheckIn, UUID> {
    List<HabitCheckIn> findAllByOwnerIdAndHabitIdAndLocalDateBetweenOrderByLocalDateAsc(UUID ownerId, UUID habitId, LocalDate start, LocalDate end);
    List<HabitCheckIn> findAllByOwnerIdAndHabitIdOrderByLocalDateDesc(UUID ownerId, UUID habitId);
    Optional<HabitCheckIn> findByOwnerIdAndHabitIdAndLocalDate(UUID ownerId, UUID habitId, LocalDate date);
    long countByOwnerIdAndLocalDateBetweenAndPerformedTrue(UUID ownerId, LocalDate start, LocalDate end);
}
