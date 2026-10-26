package com.taskos.time.repository;

import com.taskos.time.entity.TimeBlock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeBlockRepository extends JpaRepository<TimeBlock, UUID> {
    Optional<TimeBlock> findByIdAndOwnerId(UUID id, UUID ownerId);
    List<TimeBlock> findAllByOwnerIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
            UUID ownerId, Instant end, Instant start);
    List<TimeBlock> findAllByOwnerIdAndStatusOrderByStartAtAsc(UUID ownerId, com.taskos.time.entity.TimeBlockStatus status);
    @Query("select count(block) > 0 from TimeBlock block where block.ownerId = :ownerId and block.status = com.taskos.time.entity.TimeBlockStatus.PLANNED and block.startAt < :end and block.endAt > :start and (:excludeId is null or block.id <> :excludeId)")
    boolean existsOverlap(@Param("ownerId") UUID ownerId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("excludeId") UUID excludeId);
}
