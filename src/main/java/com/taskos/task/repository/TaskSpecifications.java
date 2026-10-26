package com.taskos.task.repository;

import com.taskos.task.dto.TaskSearchForm;
import com.taskos.task.dto.TaskSort;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.entity.TaskTag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class TaskSpecifications {
    private TaskSpecifications() {
    }

    public static Specification<Task> matching(UUID ownerId, TaskSearchForm filter, LocalDate today, TaskSort sort) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("owner").get("id"), ownerId));
            predicates.add(cb.isNull(root.get("workspace")));

            if ("ARCHIVED".equals(filter.getView())) predicates.add(cb.isNotNull(root.get("archivedAt")));
            else predicates.add(cb.isNull(root.get("archivedAt")));

            if (!filter.getStatus().isBlank()) predicates.add(cb.equal(root.get("status"), TaskStatus.valueOf(filter.getStatus())));
            if (!filter.getPriority().isBlank()) predicates.add(cb.equal(root.get("priority"), TaskPriority.valueOf(filter.getPriority())));

            switch (filter.getDue()) {
                case "OVERDUE" -> {
                    predicates.add(cb.lessThan(root.get("dueDate"), today));
                    predicates.add(active(cb, root));
                }
                case "TODAY" -> {
                    predicates.add(cb.equal(root.get("dueDate"), today));
                    predicates.add(active(cb, root));
                }
                case "WEEK" -> {
                    predicates.add(cb.between(root.get("dueDate"), today, today.plusDays(7)));
                    predicates.add(active(cb, root));
                }
                case "NO_DATE" -> predicates.add(cb.isNull(root.get("dueDate")));
                default -> { }
            }

            if ("YES".equals(filter.getSubtasks()) || "NO".equals(filter.getSubtasks())) {
                Subquery<UUID> children = query.subquery(UUID.class);
                Root<Task> child = children.from(Task.class);
                children.select(child.get("id")).where(
                        cb.equal(child.get("owner").get("id"), ownerId),
                        cb.equal(child.get("parent").get("id"), root.get("id")));
                predicates.add("YES".equals(filter.getSubtasks()) ? cb.exists(children) : cb.not(cb.exists(children)));
            }

            if (filter.getTagId() != null) {
                Join<Task, TaskTag> tag = root.join("tags", JoinType.INNER);
                predicates.add(cb.equal(tag.get("id"), filter.getTagId()));
                predicates.add(cb.equal(tag.get("owner").get("id"), ownerId));
                query.distinct(true);
            }

            if (!filter.getQ().isBlank()) {
                Join<Task, TaskTag> tag = root.join("tags", JoinType.LEFT);
                String pattern = "%" + escapeLike(filter.getQ().strip().toLowerCase(java.util.Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(tag.get("name")), pattern, '\\')));
                query.distinct(true);
            }

            if (sort == TaskSort.PRIORITY && query.getResultType() == Task.class) {
                CriteriaBuilder.Case<Integer> rank = cb.selectCase();
                rank.when(cb.equal(root.get("priority"), TaskPriority.URGENT), 4);
                rank.when(cb.equal(root.get("priority"), TaskPriority.HIGH), 3);
                rank.when(cb.equal(root.get("priority"), TaskPriority.MEDIUM), 2);
                rank.when(cb.equal(root.get("priority"), TaskPriority.LOW), 1);
                rank.otherwise(0);
                query.orderBy(cb.desc(rank), cb.desc(root.get("updatedAt")), cb.asc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate active(CriteriaBuilder cb, Root<Task> root) {
        return cb.not(root.get("status").in(TaskStatus.COMPLETED, TaskStatus.CANCELLED));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
