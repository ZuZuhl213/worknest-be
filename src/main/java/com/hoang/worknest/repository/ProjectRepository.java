package com.hoang.worknest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hoang.worknest.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByWorkspaceId(Long workspaceId);
    Optional<Project> findByWorkspaceIdAndProjectKey(Long workspaceId, String projectKey);
    Optional<Project> findByWorkspaceIdAndName(Long workspaceId, String name);
}
