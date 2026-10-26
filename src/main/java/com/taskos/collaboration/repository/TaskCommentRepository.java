package com.taskos.collaboration.repository;

import com.taskos.collaboration.entity.TaskComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
    List<TaskComment> findAllByWorkspaceIdAndTaskIdOrderByCreatedAtAsc(UUID workspaceId, UUID taskId, Pageable pageable);
    Optional<TaskComment> findByIdAndWorkspaceIdAndTaskId(UUID id, UUID workspaceId, UUID taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select comment from TaskComment comment where comment.id = :id and comment.workspaceId = :workspaceId and comment.taskId = :taskId")
    Optional<TaskComment> findLocked(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId, @Param("taskId") UUID taskId);
}
