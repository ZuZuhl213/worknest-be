package com.hoang.worknest.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.dto.notification.comment.TaskCommentCreateRequest;
import com.hoang.worknest.dto.notification.comment.TaskCommentResponse;
import com.hoang.worknest.dto.notification.comment.TaskCommentUpdateRequest;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.TaskComment;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.TaskCommentMapper;
import com.hoang.worknest.repository.TaskCommentRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.ProjectAuthorizationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskCommentService {

    private final TaskCommentRepository taskCommentRepository;
    private final TaskCommentMapper taskCommentMapper;
    private final TaskService taskService;
    private final ProjectAuthorizationService projectAuthorizationService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    @Transactional
    public TaskCommentResponse create(
        Long workspaceId,
        Long projectId,
        Long taskId,
        TaskCommentCreateRequest request
    ) {
        projectAuthorizationService.requireMember(workspaceId, projectId);
        Task task = taskService.findTaskInProject(workspaceId, projectId, taskId);
        User author = requireCurrentUserEntity();

        TaskComment comment = TaskComment.builder()
            .task(task)
            .author(author)
            .content(request.content())
            .build();
        TaskComment savedComment = taskCommentRepository.save(comment);

        activityLogService.log(
            task.getProject().getWorkspace(),
            task.getProject(),
            task,
            author,
            "TASK_COMMENT_CREATED",
            "TASK_COMMENT",
            savedComment.getId(),
            "{\"taskId\":" + task.getId() + "}"
        );

        return taskCommentMapper.toResponse(savedComment);
    }

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> getByTask(Long workspaceId, Long projectId, Long taskId) {
        projectAuthorizationService.requireAccess(workspaceId, projectId);
        taskService.findTaskInProject(workspaceId, projectId, taskId);
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
            .map(taskCommentMapper::toResponse)
            .toList();
    }

    @Transactional
    public TaskCommentResponse update(
        Long workspaceId,
        Long projectId,
        Long taskId,
        Long commentId,
        TaskCommentUpdateRequest request
    ) {
        projectAuthorizationService.requireMember(workspaceId, projectId);
        taskService.findTaskInProject(workspaceId, projectId, taskId);
        TaskComment comment = requireComment(taskId, commentId);
        User currentUser = requireCurrentUserEntity();

        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("You can only edit your own comments");
        }

        comment.setContent(request.content());
        return taskCommentMapper.toResponse(taskCommentRepository.save(comment));
    }

    @Transactional
    public void delete(Long workspaceId, Long projectId, Long taskId, Long commentId) {
        projectAuthorizationService.requireMember(workspaceId, projectId);
        taskService.findTaskInProject(workspaceId, projectId, taskId);
        TaskComment comment = requireComment(taskId, commentId);
        User currentUser = requireCurrentUserEntity();

        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("You can only delete your own comments");
        }

        taskCommentRepository.delete(comment);
    }

    private TaskComment requireComment(Long taskId, Long commentId) {
        return taskCommentRepository.findById(commentId)
            .filter(comment -> comment.getTask().getId().equals(taskId))
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
    }

    private User requireCurrentUserEntity() {
        Long userId = currentUserService.getCurrentUser().id();
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}
