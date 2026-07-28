package com.hoang.worknest.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hoang.worknest.entity.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    Page<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId, Pageable pageable);
}
