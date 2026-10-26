package com.taskos.project.repository;

import com.taskos.project.entity.ProjectMilestone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, UUID> {
    List<ProjectMilestone> findAllByProjectIdOrderByPositionAsc(UUID projectId);
    Optional<ProjectMilestone> findByIdAndProjectId(UUID id, UUID projectId);

    @Query("select milestone from ProjectMilestone milestone, Project project where project.id = milestone.projectId and project.owner.id = :ownerId and milestone.targetDate between :startDate and :endDate order by milestone.targetDate, milestone.position")
    List<ProjectMilestone> findCalendarMilestones(@Param("ownerId") UUID ownerId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
