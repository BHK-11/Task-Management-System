package com.taskos.productivity.repository;

import com.taskos.productivity.entity.WeeklyReview;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, UUID> {
    Optional<WeeklyReview> findByOwner_IdAndWeekStart(UUID ownerId, LocalDate weekStart);
}
