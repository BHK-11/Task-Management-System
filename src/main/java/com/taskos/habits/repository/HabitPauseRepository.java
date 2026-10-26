package com.taskos.habits.repository;

import com.taskos.habits.entity.HabitPause;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitPauseRepository extends JpaRepository<HabitPause, UUID> {
    List<HabitPause> findAllByOwnerIdAndHabitIdOrderByStartDateAsc(UUID ownerId, UUID habitId);
    Optional<HabitPause> findFirstByOwnerIdAndHabitIdAndEndDateIsNullOrderByStartDateDesc(UUID ownerId, UUID habitId);
}
