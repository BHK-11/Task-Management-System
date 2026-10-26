package com.taskos.goals.repository;

import com.taskos.goals.entity.Review;
import com.taskos.goals.entity.ReviewType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByOwnerIdAndTypeAndPeriodStart(UUID ownerId, ReviewType type, LocalDate start);
    List<Review> findAllByOwnerIdOrderByPeriodStartDesc(UUID ownerId);
    Optional<Review> findByIdAndOwnerId(UUID id, UUID ownerId);
}
