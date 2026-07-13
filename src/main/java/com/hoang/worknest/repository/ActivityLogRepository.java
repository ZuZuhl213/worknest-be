package com.hoang.worknest.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hoang.worknest.entity.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);
    List<ActivityLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<ActivityLog> findByTaskIdOrderByCreatedAtDesc(Long taskId);
}
