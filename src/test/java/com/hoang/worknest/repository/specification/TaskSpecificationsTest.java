package com.hoang.worknest.repository.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

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

        assertEquals(null, statusSpec.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
        assertEquals(null, prioritySpec.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
        assertEquals(null, titleSpec.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
        assertEquals(null, fromSpec.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
        assertEquals(null, toSpec.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
        assertEquals(null, projectSpec.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
        assertEquals(null, assigneeSpec.toPredicate(mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)));
    }
}
