package com.hoang.worknest.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.entity.ActivityLog;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.entity.Workspace;
import com.hoang.worknest.repository.ActivityLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(
        Workspace workspace,
        Project project,
        Task task,
        User actor,
        String action,
        String entityType,
        Long entityId,
        String details
    ) {
        ActivityLog activityLog = ActivityLog.builder()
            .workspace(workspace)
            .project(project)
            .task(task)
            .actor(actor)
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .details(details)
            .build();
        activityLogRepository.save(activityLog);
    }
}
