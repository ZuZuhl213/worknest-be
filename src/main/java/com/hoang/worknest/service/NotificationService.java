package com.hoang.worknest.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.dto.notification.NotificationResponse;
import com.hoang.worknest.entity.Notification;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.NotificationMapper;
import com.hoang.worknest.repository.NotificationRepository;
import com.hoang.worknest.security.CurrentUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final CurrentUserService currentUserService;

    @Transactional
    public void createAssignmentNotification(User assignee, String taskTitle) {
        if (assignee == null) {
            return;
        }

        Notification notification = Notification.builder()
            .user(assignee)
            .title("Task assigned")
            .content("You have been assigned to task: " + taskTitle)
            .read(Boolean.FALSE)
            .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public void createOverdueTaskNotification(User user, Long taskId, String taskTitle) {
        if (user == null) {
            return;
        }

        String title = "Task overdue";
        String content = "Task #" + taskId + " is overdue: " + taskTitle;
        notificationRepository.insertIfAbsent(
            user.getId(), title, content, "overdue-task:" + taskId
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getCurrentUserNotifications() {
        Long userId = currentUserService.getCurrentUser().id();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(notificationMapper::toResponse)
            .toList();
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long userId = currentUserService.getCurrentUser().id();
        Notification notification = notificationRepository.findById(notificationId)
            .filter(item -> item.getUser().getId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        notification.setRead(Boolean.TRUE);
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }
}
