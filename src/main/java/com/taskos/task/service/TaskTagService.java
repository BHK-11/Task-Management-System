package com.taskos.task.service;

import com.taskos.task.dto.TagForm;
import com.taskos.task.entity.TaskActivity;
import com.taskos.task.entity.TaskActivityType;
import com.taskos.task.entity.TaskTag;
import com.taskos.task.repository.TaskActivityRepository;
import com.taskos.task.repository.TaskTagRepository;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskTagService {
    private static final int MAX_TAGS_PER_USER = 100;
    private final TaskTagRepository tags;
    private final UserAccountRepository users;
    private final TaskActivityRepository activity;

    public TaskTagService(TaskTagRepository tags, UserAccountRepository users, TaskActivityRepository activity) {
        this.tags = tags;
        this.users = users;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public List<TaskTag> list(UUID ownerId) {
        return tags.findAllByOwner_IdOrderByNameAsc(ownerId);
    }

    public UUID create(UUID ownerId, TagForm form) {
        String name = canonicalName(form.getName());
        UserAccount owner = owner(ownerId);
        if (tags.countByOwner_Id(ownerId) >= MAX_TAGS_PER_USER) {
            throw new TaskRuleException("You can create up to 100 tags.");
        }
        if (tags.existsByOwner_IdAndName(ownerId, name)) throw new DuplicateTaskTagException();
        Instant now = Instant.now();
        TaskTag tag = TaskTag.create(owner, name, form.getColor(), now);
        try {
            tags.saveAndFlush(tag);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateTaskTagException();
        }
        record(owner, TaskActivityType.TAG_CREATED, "Tag created", now);
        return tag.getId();
    }

    public void update(UUID ownerId, UUID tagId, TagForm form) {
        TaskTag tag = tags.findByIdAndOwner_Id(tagId, ownerId).orElseThrow(TaskNotFoundException::new);
        String name = canonicalName(form.getName());
        boolean renamed = !tag.getName().equals(name);
        if (renamed && tags.existsByOwner_IdAndName(ownerId, name)) throw new DuplicateTaskTagException();
        tag.update(name, form.getColor());
        tags.saveAndFlush(tag);
        if (renamed) record(tag.getOwner(), TaskActivityType.TAG_RENAMED, "Tag renamed", Instant.now());
    }

    public void delete(UUID ownerId, UUID tagId) {
        TaskTag tag = tags.findByIdAndOwner_Id(tagId, ownerId).orElseThrow(TaskNotFoundException::new);
        Instant now = Instant.now();
        record(tag.getOwner(), TaskActivityType.TAG_DELETED, "Tag deleted", now);
        tags.delete(tag);
        tags.flush();
    }

    private String canonicalName(String raw) {
        if (raw == null) throw new TaskRuleException("Enter a tag name.");
        String name = raw.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > 32
                || !name.matches("(?U)[\\p{L}\\p{N}][\\p{L}\\p{N} _-]{0,31}")) {
            throw new TaskRuleException("Use 1 to 32 letters, numbers, spaces, hyphens, or underscores.");
        }
        return name;
    }

    private UserAccount owner(UUID ownerId) {
        return users.findById(ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private void record(UserAccount owner, TaskActivityType type, String summary, Instant at) {
        activity.save(TaskActivity.create(null, owner, owner, type, summary, at));
    }
}
