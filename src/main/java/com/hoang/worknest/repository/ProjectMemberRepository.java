package com.hoang.worknest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import com.hoang.worknest.entity.ProjectMember;
import com.hoang.worknest.enums.ProjectRole;

import jakarta.persistence.LockModeType;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    List<ProjectMember> findByProjectId(Long projectId);
    Page<ProjectMember> findByProjectId(Long projectId, Pageable pageable);
    List<ProjectMember> findByProjectWorkspaceIdAndUserId(Long workspaceId, Long userId);
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);
    boolean existsByProjectIdAndUserId(Long projectId, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProjectMember> findByProjectIdAndRoleOrderById(Long projectId, ProjectRole role);
    void deleteByProjectId(Long projectId);

    @Modifying
    @Query("delete from ProjectMember pm where pm.project.workspace.id = :workspaceId and pm.user.id = :userId")
    void deleteByWorkspaceAndUser(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);
}
