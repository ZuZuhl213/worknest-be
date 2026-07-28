package com.hoang.worknest.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.dto.common.PagedResponse;
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
            Map.of("taskId", task.getId())
        );

        return taskCommentMapper.toResponse(savedComment);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskCommentResponse> getByTask(
        Long workspaceId,
        Long projectId,
        Long taskId,
        int page,
        int size
    ) {
        projectAuthorizationService.requireAccess(workspaceId, projectId);
        taskService.findTaskInProject(workspaceId, projectId, taskId);
        Page<TaskComment> comments = taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(
            taskId,
            PageRequest.of(validatePage(page), validateSize(size))
        );
        return new PagedResponse<>(
            comments.getContent().stream().map(taskCommentMapper::toResponse).toList(),
            comments.getNumber(),
            comments.getSize(),
            comments.getTotalElements(),
            comments.getTotalPages(),
            comments.isFirst(),
            comments.isLast()
        );
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

    private int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        return page;
    }

    private int validateSize(int size) {
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        return size;
    }
}
