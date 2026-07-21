package com.hoang.worknest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hoang.worknest.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByWorkspaceId(Long workspaceId);

    @Query("""
        select distinct p from Project p
        join ProjectMember pm on pm.project = p
        where p.workspace.id = :workspaceId and pm.user.id = :userId
        order by p.createdAt desc
        """)
    List<Project> findAccessibleByWorkspaceAndUser(
        @Param("workspaceId") Long workspaceId,
        @Param("userId") Long userId
    );
    Optional<Project> findByWorkspaceIdAndProjectKey(Long workspaceId, String projectKey);
    Optional<Project> findByWorkspaceIdAndName(Long workspaceId, String name);
}
