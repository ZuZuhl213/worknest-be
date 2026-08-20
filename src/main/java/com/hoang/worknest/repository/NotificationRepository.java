package com.hoang.worknest.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hoang.worknest.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query(value = """
        insert into notifications (user_id, title, content, deduplication_key, is_read)
        values (:userId, :title, :content, :deduplicationKey, false)
        on conflict (user_id, deduplication_key) where deduplication_key is not null do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("userId") Long userId,
        @Param("title") String title,
        @Param("content") String content,
        @Param("deduplicationKey") String deduplicationKey
    );
}
