package com.taskos.habits.repository;

import com.taskos.habits.entity.Habit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface HabitRepository extends JpaRepository<Habit, UUID> {
    List<Habit> findAllByOwnerIdAndStatusOrderByNameAsc(UUID ownerId, String status);
    Optional<Habit> findByIdAndOwnerId(UUID id, UUID ownerId);
    long countByOwnerIdAndStatus(UUID ownerId, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select habit from Habit habit where habit.id = :id and habit.ownerId = :ownerId")
    Optional<Habit> findLockedByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
