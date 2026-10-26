package com.taskos.productivity.repository;

import com.taskos.productivity.entity.DailyPlanEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPlanEntryRepository extends JpaRepository<DailyPlanEntry, UUID> {
    List<DailyPlanEntry> findAllByPlan_IdOrderByPositionAsc(UUID planId);
    Optional<DailyPlanEntry> findByPlan_IdAndTask_Id(UUID planId, UUID taskId);
    long countByPlan_IdAndBucket(UUID planId, com.taskos.productivity.entity.PlanBucket bucket);
    void deleteAllByPlan_Id(UUID planId);
}
