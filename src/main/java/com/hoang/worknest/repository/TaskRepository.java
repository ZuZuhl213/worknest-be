package com.hoang.worknest.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hoang.worknest.entity.Task;
import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    List<Task> findByProjectId(Long projectId);
    Page<Task> findByProjectId(Long projectId, Pageable pageable);
    Page<Task> findByProjectIdAndStatus(Long projectId, TaskStatus status, Pageable pageable);
    Page<Task> findByProjectIdAndPriority(Long projectId, TaskPriority priority, Pageable pageable);
    Page<Task> findByProjectIdAndAssigneeId(Long projectId, Long assigneeId, Pageable pageable);
    List<Task> findByDueDateBeforeAndStatusNot(OffsetDateTime dueDate, TaskStatus status);

    @Query("select coalesce(max(task.taskNumber), 0) from Task task where task.project.id = :projectId")
    long findMaxTaskNumberByProjectId(@Param("projectId") Long projectId);
}
