package com.hoang.worknest.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.enums.TaskStatus;
import com.hoang.worknest.repository.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OverdueTaskService {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    @Transactional
    public int notifyOverdueTasks() {
        List<Task> overdueTasks = taskRepository.findByDueDateBeforeAndStatusNot(
            OffsetDateTime.now(),
            TaskStatus.DONE
        );

        overdueTasks.forEach(this::notifyTaskOwner);
        return overdueTasks.size();
    }

    private void notifyTaskOwner(Task task) {
        User notificationTarget = task.getAssignee() != null ? task.getAssignee() : task.getReporter();
        notificationService.createOverdueTaskNotification(notificationTarget, task.getId(), task.getTitle());
    }
}
