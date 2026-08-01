package com.hoang.worknest.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("select u from User u where (:active is null or u.isActive = :active)")
    Page<User> searchAll(Boolean active, Pageable pageable);

    @Query("select u from User u where " +
        "(lower(u.email) like concat('%', lower(:search), '%') " +
        "or lower(u.fullName) like concat('%', lower(:search), '%')) " +
        "and (:active is null or u.isActive = :active)")
    Page<User> searchByText(String search, Boolean active, Pageable pageable);
}
