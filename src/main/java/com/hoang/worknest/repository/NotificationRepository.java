package com.hoang.worknest.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hoang.worknest.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByUserIdAndTitleAndContent(Long userId, String title, String content);
}
