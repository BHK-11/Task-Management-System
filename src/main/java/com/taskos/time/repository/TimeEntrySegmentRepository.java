package com.taskos.time.repository;

import com.taskos.time.entity.TimeEntrySegment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntrySegmentRepository extends JpaRepository<TimeEntrySegment, UUID> {
    List<TimeEntrySegment> findAllByEntryIdOrderByStartedAtAsc(UUID entryId);
    @Query("select segment from TimeEntrySegment segment where segment.ownerId = :ownerId and segment.startedAt < :end and (segment.endedAt is null or segment.endedAt > :start) order by segment.startedAt")
    List<TimeEntrySegment> findOverlapping(@Param("ownerId") UUID ownerId,
            @Param("start") Instant start, @Param("end") Instant end);
    @Query("select segment from TimeEntrySegment segment, TimeEntry entry where segment.entryId = entry.id and segment.ownerId = :ownerId and entry.source = com.taskos.time.entity.TimeEntrySource.FOCUS_SESSION and segment.startedAt < :end and (segment.endedAt is null or segment.endedAt > :start) order by segment.startedAt")
    List<TimeEntrySegment> findFocusOverlapping(@Param("ownerId") UUID ownerId,
            @Param("start") Instant start, @Param("end") Instant end);
}
