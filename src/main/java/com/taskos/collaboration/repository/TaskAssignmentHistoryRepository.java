package com.taskos.collaboration.repository;

import com.taskos.collaboration.entity.TaskAssignmentHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAssignmentHistoryRepository extends JpaRepository<TaskAssignmentHistory, UUID> { }
