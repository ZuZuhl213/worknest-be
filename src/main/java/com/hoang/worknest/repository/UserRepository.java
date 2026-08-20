package com.hoang.worknest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.hoang.worknest.enums.SystemRole;

import com.hoang.worknest.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByGoogleSubject(String googleSubject);
    boolean existsByEmailIgnoreCase(String email);
    long countBySystemRoleAndIsActiveTrue(SystemRole systemRole);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findBySystemRoleAndIsActiveTrueOrderById(SystemRole systemRole);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    long countByIsActiveTrue();
    long countByIsActiveFalse();
    long countByEmailVerifiedTrue();

    @Query("""
        select u from User u
        where (:search = '' or (lower(u.email) like concat('%', lower(:search), '%')
            or lower(u.fullName) like concat('%', lower(:search), '%')))
        and (:active is null or u.isActive = :active)
        and (:emailVerified is null or u.emailVerified = :emailVerified)
        and (:role is null or u.systemRole = :role)
        """)
    Page<User> search(String search, Boolean active, Boolean emailVerified, SystemRole role, Pageable pageable);
}
