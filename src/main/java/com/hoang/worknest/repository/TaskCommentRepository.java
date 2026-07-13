package com.hoang.worknest.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hoang.worknest.entity.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);
}
