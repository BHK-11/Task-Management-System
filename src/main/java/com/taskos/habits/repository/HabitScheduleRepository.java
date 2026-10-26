package com.taskos.habits.repository;

import com.taskos.habits.entity.HabitSchedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitScheduleRepository extends JpaRepository<HabitSchedule, UUID> {
    List<HabitSchedule> findAllByOwnerIdAndHabitIdOrderByEffectiveFromAsc(UUID ownerId, UUID habitId);
    Optional<HabitSchedule> findFirstByOwnerIdAndHabitIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(UUID ownerId, UUID habitId, LocalDate date);
    Optional<HabitSchedule> findByOwnerIdAndHabitIdAndEffectiveFrom(UUID ownerId, UUID habitId, LocalDate date);
}
