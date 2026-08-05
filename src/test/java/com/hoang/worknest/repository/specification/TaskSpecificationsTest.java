package com.hoang.worknest.repository.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import com.hoang.worknest.entity.Task;
import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

class TaskSpecificationsTest {

    @Test
    void hasStatusReturnsNullForNullStatus() {
        Specification<Task> specification = TaskSpecifications.hasStatus(null);
        assertNull(specification.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
    }

    @Test
    void hasPriorityReturnsNullForNullPriority() {
        Specification<Task> specification = TaskSpecifications.hasPriority(null);
        assertNull(specification.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
    }

    @Test
    void titleContainsReturnsNullForBlankKeyword() {
        Specification<Task> specification = TaskSpecifications.titleContains("   ");
        assertNull(specification.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
    }

    @Test
    void dueDateFromAndToReturnNullForNullInputs() {
        assertNull(TaskSpecifications.dueDateFrom(null).toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
        assertNull(TaskSpecifications.dueDateTo(null).toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
    }

    @Test
    void buildsPredicatesForRelevantInputs() {
        Specification<Task> statusSpec = TaskSpecifications.hasStatus(TaskStatus.TODO);
        Specification<Task> prioritySpec = TaskSpecifications.hasPriority(TaskPriority.HIGH);
        Specification<Task> titleSpec = TaskSpecifications.titleContains("design");
        Specification<Task> fromSpec = TaskSpecifications.dueDateFrom(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
        Specification<Task> toSpec = TaskSpecifications.dueDateTo(OffsetDateTime.parse("2024-01-02T00:00:00Z"));
        Specification<Task> projectSpec = TaskSpecifications.belongsToProject(11L);
        Specification<Task> assigneeSpec = TaskSpecifications.hasAssignee(77L);

        Root<Task> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> statusPath = mock(Path.class);
        Path<Object> priorityPath = mock(Path.class);
        Path<String> titlePath = mock(Path.class);
        Path<String> loweredTitlePath = mock(Path.class);
        Path<OffsetDateTime> dueDatePath = mock(Path.class);
        Path<Object> projectPath = mock(Path.class);
        Path<Object> projectIdPath = mock(Path.class);
        Path<Object> assigneePath = mock(Path.class);
        Path<Object> assigneeIdPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        doReturn(statusPath).when(root).get("status");
        doReturn(priorityPath).when(root).get("priority");
        doReturn(titlePath).when(root).get("title");
        doReturn(dueDatePath).when(root).get("dueDate");
        doReturn(projectPath).when(root).get("project");
        doReturn(projectIdPath).when(projectPath).get("id");
        doReturn(assigneePath).when(root).get("assignee");
        doReturn(assigneeIdPath).when(assigneePath).get("id");
        when(cb.equal(statusPath, TaskStatus.TODO)).thenReturn(predicate);
        when(cb.equal(priorityPath, TaskPriority.HIGH)).thenReturn(predicate);
        when(cb.lower(titlePath)).thenReturn(loweredTitlePath);
        when(cb.like(loweredTitlePath, "%design%")).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(dueDatePath, OffsetDateTime.parse("2024-01-01T00:00:00Z"))).thenReturn(predicate);
        when(cb.lessThanOrEqualTo(dueDatePath, OffsetDateTime.parse("2024-01-02T00:00:00Z"))).thenReturn(predicate);
        when(cb.equal(projectIdPath, 11L)).thenReturn(predicate);
        when(cb.equal(assigneeIdPath, 77L)).thenReturn(predicate);

        assertEquals(predicate, statusSpec.toPredicate(root, query, cb));
        assertEquals(predicate, prioritySpec.toPredicate(root, query, cb));
        assertEquals(predicate, titleSpec.toPredicate(root, query, cb));
        assertEquals(predicate, fromSpec.toPredicate(root, query, cb));
        assertEquals(predicate, toSpec.toPredicate(root, query, cb));
        assertEquals(predicate, projectSpec.toPredicate(root, query, cb));
        assertEquals(predicate, assigneeSpec.toPredicate(root, query, cb));
    }
}
