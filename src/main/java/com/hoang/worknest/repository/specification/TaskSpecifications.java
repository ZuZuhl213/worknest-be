package com.hoang.worknest.repository.specification;

import java.time.OffsetDateTime;
import java.util.Collection;

import org.springframework.data.jpa.domain.Specification;

import com.hoang.worknest.entity.Task;
import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;

public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> belongsToProject(Long projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Task> belongsToWorkspace(Long workspaceId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("workspace").get("id"), workspaceId);
    }

    public static Specification<Task> projectIn(Collection<Long> projectIds) {
        return (root, query, cb) -> projectIds == null ? null : root.get("project").get("id").in(projectIds);
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(TaskPriority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> hasAssignee(Long assigneeId) {
        return (root, query, cb) -> assigneeId == null ? null : cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<Task> titleContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
        };
    }

    public static Specification<Task> dueDateFrom(OffsetDateTime dueFrom) {
        return (root, query, cb) -> dueFrom == null ? null : cb.greaterThanOrEqualTo(root.get("dueDate"), dueFrom);
    }

    public static Specification<Task> dueDateTo(OffsetDateTime dueTo) {
        return (root, query, cb) -> dueTo == null ? null : cb.lessThanOrEqualTo(root.get("dueDate"), dueTo);
    }
}
