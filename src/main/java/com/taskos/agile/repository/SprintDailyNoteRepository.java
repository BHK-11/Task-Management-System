package com.taskos.agile.repository;

import com.taskos.agile.entity.SprintDailyNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintDailyNoteRepository extends JpaRepository<SprintDailyNote, UUID> {
    List<SprintDailyNote> findAllBySprintIdOrderByCreatedAtDesc(UUID sprintId);
}
