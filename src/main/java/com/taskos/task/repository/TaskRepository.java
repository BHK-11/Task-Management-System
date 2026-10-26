package com.taskos.task.repository;

import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.dashboard.dto.DashboardMetricTotals;
import com.taskos.collaboration.entity.TaskAssignment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from Task task where task.id = :taskId and task.owner.id = :ownerId and task.workspace is null")
    Optional<Task> findLockedByIdAndOwner(@Param("taskId") UUID taskId, @Param("ownerId") UUID ownerId);

    @Query("""
            select task from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
                and task.status in :statuses
            order by task.updatedAt desc, task.id asc
            """)
    List<Task> findProductivityTasks(@Param("ownerId") UUID ownerId,
            @Param("statuses") Collection<TaskStatus> statuses, Pageable pageable);

    @Query("""
            select task from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
              and task.id not in (select placement.taskId from ProjectTaskPlacement placement where placement.ownerId = :ownerId)
            order by task.updatedAt desc
            """)
    List<Task> findAvailableProjectTasks(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("""
            select distinct task from Task task
            left join fetch task.tags
            left join fetch task.parent
            where task.id = :taskId and task.owner.id = :ownerId and task.workspace is null
            """)
    Optional<Task> findDetailByIdAndOwner(@Param("taskId") UUID taskId, @Param("ownerId") UUID ownerId);

    @Query("select task from Task task where task.owner.id = :ownerId and task.parent.id = :parentId and task.workspace is null order by task.subtaskPosition asc, task.createdAt asc")
    List<Task> findByOwner_IdAndParent_IdOrderBySubtaskPositionAscCreatedAtAsc(@Param("ownerId") UUID ownerId, @Param("parentId") UUID parentId);

    @Query("select task from Task task where task.owner.id = :ownerId and task.parent.id in :parentIds and task.workspace is null")
    List<Task> findAllByOwner_IdAndParent_IdIn(@Param("ownerId") UUID ownerId, @Param("parentIds") Collection<UUID> parentIds);

    @Query("select task from Task task where task.owner.id = :ownerId and task.id in :ids and task.workspace is null")
    List<Task> findAllByOwner_IdAndIdIn(@Param("ownerId") UUID ownerId, @Param("ids") Collection<UUID> ids);

    @Query("select task from Task task where task.owner.id = :ownerId and task.workspace is null and task.parent is null "
            + "and task.archivedAt is null and task.dueDate >= :startDate and task.dueDate <= :endDate order by task.dueDate asc")
    List<Task> findAllByOwner_IdAndParentIsNullAndArchivedAtIsNullAndDueDateGreaterThanEqualAndDueDateLessThanEqualOrderByDueDateAsc(
            @Param("ownerId") UUID ownerId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("select task from Task task where task.owner.id = :ownerId and task.workspace is null and task.parent is null "
            + "and task.archivedAt is null and task.dueDate >= :startDate and task.dueDate <= :endDate order by task.dueDate asc")
    Page<Task> findAllByOwner_IdAndParentIsNullAndArchivedAtIsNullAndDueDateGreaterThanEqualAndDueDateLessThanEqualOrderByDueDateAsc(
            @Param("ownerId") UUID ownerId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

    @Query("""
            select distinct task from Task task left join fetch task.tags
            where task.workspace.id = :workspaceId and task.parent is null and task.archivedAt is null
              and task.dueDate between :startDate and :endDate
            order by task.dueDate asc, task.updatedAt asc, task.id asc
            """)
    List<Task> findWorkspaceCalendarTasks(@Param("workspaceId") UUID workspaceId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

    @Query("select task from Task task join task.tags tag where task.owner.id = :ownerId and task.workspace is null and tag.id = :tagId")
    List<Task> findAllByOwner_IdAndTags_Id(@Param("ownerId") UUID ownerId, @Param("tagId") UUID tagId);

    @Query("""
            select distinct task from Task task
            left join fetch task.tags
            left join fetch task.parent
            where task.owner.id = :ownerId and task.workspace is null and task.id in :ids
            """)
    List<Task> findPageDetailsByOwnerAndIds(@Param("ownerId") UUID ownerId, @Param("ids") Collection<UUID> ids);

    @Query("""
            select task.parent.id, task.status, count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent.id in :parentIds
            group by task.parent.id, task.status
            """)
    List<Object[]> countChildrenByParentIds(@Param("ownerId") UUID ownerId, @Param("parentIds") Collection<UUID> parentIds);

    @Query("select count(task) from Task task where task.owner.id = :ownerId and task.workspace is null")
    long countByOwner_Id(@Param("ownerId") UUID ownerId);

    @Query("""
            select new com.taskos.dashboard.dto.DashboardMetricTotals(
                count(case when task.status in :activeStatuses then task.id end),
                count(case when task.status in :activeStatuses and task.dueDate = :today then task.id end),
                count(case when task.status in :activeStatuses and task.dueDate = :tomorrow then task.id end),
                count(case when task.status in :activeStatuses and task.dueDate >= :tomorrow
                    and task.dueDate < :weekEndExclusive then task.id end),
                count(case when task.status in :activeStatuses and task.dueDate < :today then task.id end),
                count(case when task.status in :activeStatuses and task.dueDate is null then task.id end),
                count(case when task.status = :completed and task.completedAt >= :todayStart
                    and task.completedAt < :tomorrowStart then task.id end),
                count(case when task.status = :completed and task.completedAt >= :weekStart
                    and task.completedAt < :tomorrowStart then task.id end))
            from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
            and (task.status in :activeStatuses or task.status = :completed)
            """)
    DashboardMetricTotals dashboardMetricTotals(@Param("ownerId") UUID ownerId,
            @Param("activeStatuses") Collection<TaskStatus> activeStatuses,
            @Param("completed") TaskStatus completed, @Param("today") LocalDate today,
            @Param("tomorrow") LocalDate tomorrow, @Param("weekEndExclusive") LocalDate weekEndExclusive,
            @Param("weekStart") Instant weekStart, @Param("todayStart") Instant todayStart,
            @Param("tomorrowStart") Instant tomorrowStart);

    @Query("""
            select count(task) from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
            and task.status = :completed and task.completedAt >= :from and task.completedAt < :until
            """)
    long countDashboardCompletedBetween(@Param("ownerId") UUID ownerId, @Param("completed") TaskStatus completed,
            @Param("from") Instant from, @Param("until") Instant until);

    @Query("""
            select task from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
            and task.status in :statuses and task.dueDate = :date
            order by case when task.priority = :urgent then 0 when task.priority = :high then 1 else 2 end,
                task.updatedAt asc, task.id asc
            """)
    Page<Task> findDashboardDueOn(@Param("ownerId") UUID ownerId, @Param("statuses") Collection<TaskStatus> statuses,
            @Param("date") LocalDate date, @Param("urgent") TaskPriority urgent, @Param("high") TaskPriority high,
            Pageable pageable);

    @Query("""
            select task from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
            and task.status in :statuses and task.dueDate < :before
            order by task.dueDate asc, case when task.priority = :urgent then 0 when task.priority = :high then 1 else 2 end,
                task.updatedAt asc, task.id asc
            """)
    Page<Task> findDashboardOverdue(@Param("ownerId") UUID ownerId, @Param("statuses") Collection<TaskStatus> statuses,
            @Param("before") LocalDate before, @Param("urgent") TaskPriority urgent, @Param("high") TaskPriority high,
            Pageable pageable);

    @Query("""
            select task from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
            and task.status in :statuses and task.dueDate >= :from
            order by task.dueDate asc, case when task.priority = :urgent then 0 when task.priority = :high then 1 else 2 end,
                task.updatedAt asc, task.id asc
            """)
    Page<Task> findDashboardUpcoming(@Param("ownerId") UUID ownerId, @Param("statuses") Collection<TaskStatus> statuses,
            @Param("from") LocalDate from, @Param("urgent") TaskPriority urgent, @Param("high") TaskPriority high,
            Pageable pageable);

    @Query("""
            select task from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
            and task.status in :statuses
            and ((task.dueDate is not null and task.dueDate <= :today) or task.priority in :priorities)
            order by case when task.dueDate < :today then 0 when task.dueDate = :today then 1
                when task.priority = :urgent then 2 when task.priority = :high then 3 else 4 end,
                case when task.priority = :urgent then 0 when task.priority = :high then 1 else 2 end,
                case when task.dueDate is null then 1 else 0 end, task.dueDate asc, task.updatedAt asc, task.id asc
            """)
    Page<Task> findDashboardMyDay(@Param("ownerId") UUID ownerId, @Param("statuses") Collection<TaskStatus> statuses,
            @Param("today") LocalDate today, @Param("priorities") Collection<TaskPriority> priorities,
            @Param("urgent") TaskPriority urgent, @Param("high") TaskPriority high, Pageable pageable);

    @Query("""
            select task from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
            and task.status in :statuses and task.priority in :priorities
            order by case when task.priority = :urgent then 0 else 1 end,
                case when task.dueDate is null then 1 else 0 end, task.dueDate asc, task.updatedAt asc, task.id asc
            """)
    List<Task> findDashboardPriorities(@Param("ownerId") UUID ownerId, @Param("statuses") Collection<TaskStatus> statuses,
            @Param("priorities") Collection<TaskPriority> priorities, @Param("urgent") TaskPriority urgent,
            Pageable pageable);

    @Query("""
            select task from Task task
            where task.owner.id = :ownerId and task.workspace is null and task.parent is null and task.archivedAt is null
            and task.status = :completed and task.completedAt is not null
            order by task.completedAt desc, task.id asc
            """)
    List<Task> findDashboardRecentlyCompleted(@Param("ownerId") UUID ownerId,
            @Param("completed") TaskStatus completed, Pageable pageable);

    @Query("select count(task) from Task task where task.owner.id = :ownerId and task.workspace is null and task.parent.id = :parentId")
    long countByOwner_IdAndParent_Id(@Param("ownerId") UUID ownerId, @Param("parentId") UUID parentId);

    @Query("select (count(task) > 0) from Task task where task.owner.id = :ownerId and task.workspace is null and task.parent.id = :parentId")
    boolean existsByOwner_IdAndParent_Id(@Param("ownerId") UUID ownerId, @Param("parentId") UUID parentId);

    @Query("select (count(task) > 0) from Task task join task.tags tag where task.owner.id = :ownerId and task.workspace is null and tag.id = :tagId")
    boolean existsByOwner_IdAndTags_Id(@Param("ownerId") UUID ownerId, @Param("tagId") UUID tagId);

    @Query("select task from Task task where task.id = :taskId and task.workspace.id = :workspaceId")
    Optional<Task> findByIdAndWorkspace(@Param("taskId") UUID taskId, @Param("workspaceId") UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from Task task where task.id = :taskId and task.workspace.id = :workspaceId")
    Optional<Task> findLockedByIdAndWorkspace(@Param("taskId") UUID taskId, @Param("workspaceId") UUID workspaceId);

    List<Task> findAllByWorkspace_IdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID workspaceId, Pageable pageable);

    long countByWorkspace_IdAndStatusInAndArchivedAtIsNull(UUID workspaceId, Collection<TaskStatus> statuses);

    List<Task> findAllByWorkspace_IdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    @Query("select task from Task task, TaskAssignment assignment where assignment.workspaceId = :workspaceId "
            + "and assignment.assigneeId = :assigneeId and assignment.taskId = task.id "
            + "and task.workspace.id = :workspaceId and task.archivedAt is null order by task.dueDate asc, task.updatedAt desc")
    List<Task> findAssignedWorkspaceTasks(@Param("workspaceId") UUID workspaceId, @Param("assigneeId") UUID assigneeId,
            Pageable pageable);

    @Query("select assignment.assigneeId, count(task), "
            + "sum(case when task.dueDate between :today and :dueSoon then 1 else 0 end), "
            + "sum(case when task.dueDate < :today then 1 else 0 end), "
            + "sum(case when task.priority in :highPriorities then 1 else 0 end) "
            + "from TaskAssignment assignment, Task task where assignment.workspaceId = :workspaceId "
            + "and assignment.taskId = task.id and task.workspace.id = :workspaceId and task.archivedAt is null "
            + "and task.status in :openStatuses group by assignment.assigneeId")
    List<Object[]> workspaceWorkload(@Param("workspaceId") UUID workspaceId,
            @Param("today") LocalDate today, @Param("dueSoon") LocalDate dueSoon,
            @Param("openStatuses") Collection<TaskStatus> openStatuses,
            @Param("highPriorities") Collection<TaskPriority> highPriorities);

    @Query("select count(task) from Task task where task.workspace.id = :workspaceId and task.archivedAt is null "
            + "and task.status in :openStatuses and not exists (select assignment.taskId from TaskAssignment assignment "
            + "where assignment.workspaceId = :workspaceId and assignment.taskId = task.id)")
    long countUnassignedWorkspaceTasks(@Param("workspaceId") UUID workspaceId,
            @Param("openStatuses") Collection<TaskStatus> openStatuses);
}
