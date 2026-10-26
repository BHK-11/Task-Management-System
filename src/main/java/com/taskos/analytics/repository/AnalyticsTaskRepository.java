package com.taskos.analytics.repository;

import com.taskos.task.entity.TaskStatus;
import com.taskos.task.entity.TaskActivityType;
import com.taskos.task.entity.Task;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsTaskRepository extends JpaRepository<Task, UUID> {
    @Query("""
            select task.status, count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            group by task.status
            """)
    List<Object[]> statusCounts(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId);

    @Query("""
            select task.priority, count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and task.status in :openStatuses
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            group by task.priority
            """)
    List<Object[]> openPriorityCounts(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("openStatuses") List<TaskStatus> openStatuses);

    @Query("""
            select count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and task.createdAt >= :from and task.createdAt < :until
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            """)
    long createdBetween(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("from") Instant from, @Param("until") Instant until);

    @Query("""
            select count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and task.status = :completed and task.completedAt >= :from and task.completedAt < :until
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            """)
    long completedBetween(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("completed") TaskStatus completed, @Param("from") Instant from, @Param("until") Instant until);

    @Query("""
            select count(activity) from TaskActivity activity, Task task
            where activity.taskId = task.id and activity.owner.id = :ownerId and activity.eventType = :eventType
              and task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and activity.occurredAt >= :from and activity.occurredAt < :until
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            """)
    long completionEventCount(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("eventType") TaskActivityType eventType, @Param("from") Instant from, @Param("until") Instant until);

    @Query("""
            select activity.occurredAt from TaskActivity activity, Task task
            where activity.taskId = task.id and activity.owner.id = :ownerId and activity.eventType = :eventType
              and task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and activity.occurredAt >= :from and activity.occurredAt < :until
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            order by activity.occurredAt asc
            """)
    List<Instant> completionEventSamples(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("eventType") TaskActivityType eventType, @Param("from") Instant from, @Param("until") Instant until,
            Pageable pageable);

    @Query("""
            select count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and task.status in :openStatuses and task.dueDate < :today
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            """)
    long overdueCount(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("openStatuses") List<TaskStatus> openStatuses, @Param("today") LocalDate today);

    @Query("""
            select count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and task.status in :openStatuses and task.dueDate = :date
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            """)
    long dueOn(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("openStatuses") List<TaskStatus> openStatuses, @Param("date") LocalDate date);

    @Query("""
            select count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and task.status in :openStatuses and task.dueDate >= :from and task.dueDate <= :until
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            """)
    long dueBetween(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("openStatuses") List<TaskStatus> openStatuses, @Param("from") LocalDate from,
            @Param("until") LocalDate until);

    @Query("""
            select count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and task.status in :openStatuses and task.dueDate is null
              and (:projectId is null or exists (select placement.taskId from ProjectTaskPlacement placement
                  where placement.ownerId = :ownerId and placement.projectId = :projectId and placement.taskId = task.id))
            """)
    long unscheduledCount(@Param("ownerId") UUID ownerId, @Param("projectId") UUID projectId,
            @Param("openStatuses") List<TaskStatus> openStatuses);
}
