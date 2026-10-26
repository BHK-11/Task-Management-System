package com.taskos.collaboration.repository;

import com.taskos.collaboration.entity.TaskCommentMention;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCommentMentionRepository extends JpaRepository<TaskCommentMention, TaskCommentMention.Key> {
    List<TaskCommentMention> findAllByCommentId(UUID commentId);
    void deleteAllByCommentId(UUID commentId);
}
