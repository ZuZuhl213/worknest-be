package com.hoang.worknest.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hoang.worknest.entity.SecurityAuditLog;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {
    Page<SecurityAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
