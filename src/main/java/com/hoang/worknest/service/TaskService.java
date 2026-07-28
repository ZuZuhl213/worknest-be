package com.hoang.worknest.service;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hoang.worknest.config.CacheConfig;
import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.task.AttachmentResponse;
import com.hoang.worknest.dto.task.TaskAssignRequest;
import com.hoang.worknest.dto.task.TaskCreateRequest;
import com.hoang.worknest.dto.task.TaskResponse;
import com.hoang.worknest.dto.task.TaskUpdateRequest;
import com.hoang.worknest.entity.Attachment;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.exception.ServiceUnavailableException;
import com.hoang.worknest.mapper.TaskMapper;
import com.hoang.worknest.mapper.UserMapper;
import com.hoang.worknest.repository.AttachmentRepository;
import com.hoang.worknest.repository.TaskRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.repository.ProjectMemberRepository;
import com.hoang.worknest.repository.specification.TaskSpecifications;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.ProjectAuthorizationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "createdAt", "updatedAt", "dueDate", "priority", "status", "taskNumber", "title"
    );

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final ProjectAuthorizationService projectAuthorizationService;

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TASKS_BY_PROJECT, key = "#projectId")
    public TaskResponse create(Long workspaceId, Long projectId, TaskCreateRequest request) {
        Project project = projectAuthorizationService.requireMember(workspaceId, projectId);
        User reporter = requireCurrentUserEntity();
        if (request.assigneeUserId() != null) {
            projectAuthorizationService.requireLead(workspaceId, projectId);
        }
        User assignee = resolveAssignee(projectId, request.assigneeUserId());

        long nextTaskNumber = taskRepository.findMaxTaskNumberByProjectId(projectId) + 1L;

        Task task = taskMapper.toEntity(request);
        task.setProject(project);
        task.setReporter(reporter);
        task.setAssignee(assignee);
        task.setTaskNumber(nextTaskNumber);

        Task savedTask = taskRepository.save(task);
        if (assignee != null) {
            notificationService.createAssignmentNotification(assignee, savedTask.getTitle());
        }
        activityLogService.log(
            project.getWorkspace(),
            project,
            savedTask,
            reporter,
            "TASK_CREATED",
            "TASK",
            savedTask.getId(),
            Map.of("status", savedTask.getStatus().name())
        );
        return taskMapper.toResponse(savedTask);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> getByProject(
        Long workspaceId,
        Long projectId,
        TaskStatus status,
        TaskPriority priority,
        Long assigneeId,
        String search,
        OffsetDateTime dueFrom,
        OffsetDateTime dueTo,
        int page,
        int size,
        String sortBy,
        String sortDirection
    ) {
        projectAuthorizationService.requireAccess(workspaceId, projectId);
        validatePageRequest(page, size, sortBy);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Specification<Task> specification = Specification.where(TaskSpecifications.belongsToProject(projectId))
            .and(TaskSpecifications.hasStatus(status))
            .and(TaskSpecifications.hasPriority(priority))
            .and(TaskSpecifications.hasAssignee(assigneeId))
            .and(TaskSpecifications.titleContains(search))
            .and(TaskSpecifications.dueDateFrom(dueFrom))
            .and(TaskSpecifications.dueDateTo(dueTo));

        Page<Task> taskPage = taskRepository.findAll(specification, pageable);
        return new PagedResponse<>(
            taskPage.getContent().stream().map(taskMapper::toResponse).toList(),
            taskPage.getNumber(),
            taskPage.getSize(),
            taskPage.getTotalElements(),
            taskPage.getTotalPages(),
            taskPage.isFirst(),
            taskPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(Long workspaceId, Long projectId, Long taskId) {
        projectAuthorizationService.requireAccess(workspaceId, projectId);
        Task task = findTaskInProject(workspaceId, projectId, taskId);
        return taskMapper.toResponse(task);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.TASKS_BY_PROJECT, key = "#projectId"),
        @CacheEvict(cacheNames = CacheConfig.TASK_DETAIL, key = "#taskId")
    })
    public TaskResponse update(Long workspaceId, Long projectId, Long taskId, TaskUpdateRequest request) {
        Task task = findTaskInProject(workspaceId, projectId, taskId);
        projectAuthorizationService.requireTaskWriter(workspaceId, projectId, task);
        User actor = requireCurrentUserEntity();
        Long oldAssigneeId = task.getAssignee() != null ? task.getAssignee().getId() : null;
        if (!java.util.Objects.equals(oldAssigneeId, request.assigneeUserId())) {
            projectAuthorizationService.requireLead(workspaceId, projectId);
        }
        User assignee = resolveAssignee(projectId, request.assigneeUserId());

        taskMapper.updateEntity(request, task);
        task.setAssignee(assignee);
        applyTimeline(task);

        Task savedTask = taskRepository.save(task);
        if (assignee != null && !assignee.getId().equals(oldAssigneeId)) {
            notificationService.createAssignmentNotification(assignee, savedTask.getTitle());
        }
        activityLogService.log(
            task.getProject().getWorkspace(),
            task.getProject(),
            savedTask,
            actor,
            "TASK_UPDATED",
            "TASK",
            savedTask.getId(),
            Map.of(
                "status", savedTask.getStatus().name(),
                "priority", savedTask.getPriority().name()
            )
        );
        return taskMapper.toResponse(savedTask);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.TASKS_BY_PROJECT, key = "#projectId"),
        @CacheEvict(cacheNames = CacheConfig.TASK_DETAIL, key = "#taskId")
    })
    public TaskResponse assign(Long workspaceId, Long projectId, Long taskId, TaskAssignRequest request) {
        projectAuthorizationService.requireLead(workspaceId, projectId);
        Task task = findTaskInProject(workspaceId, projectId, taskId);
        User actor = requireCurrentUserEntity();
        User assignee = resolveAssignee(projectId, request.assigneeUserId());
        Long oldAssigneeId = task.getAssignee() != null ? task.getAssignee().getId() : null;

        task.setAssignee(assignee);
        Task savedTask = taskRepository.save(task);
        if (!assignee.getId().equals(oldAssigneeId)) {
            notificationService.createAssignmentNotification(assignee, savedTask.getTitle());
        }
        activityLogService.log(
            task.getProject().getWorkspace(),
            task.getProject(),
            savedTask,
            actor,
            "TASK_ASSIGNED",
            "TASK",
            savedTask.getId(),
            Map.of("assigneeUserId", assignee.getId())
        );
        return taskMapper.toResponse(savedTask);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.TASKS_BY_PROJECT, key = "#projectId"),
        @CacheEvict(cacheNames = CacheConfig.TASK_DETAIL, key = "#taskId")
    })
    public void delete(Long workspaceId, Long projectId, Long taskId) {
        projectAuthorizationService.requireAccess(workspaceId, projectId);
        Task task = findTaskInProject(workspaceId, projectId, taskId);
        User currentUser = requireCurrentUserEntity();

        // Reporter can always delete their own task.
        // Otherwise, user must be project LEAD or workspace ADMIN/OWNER.
        boolean isReporter = task.getReporter() != null && task.getReporter().getId().equals(currentUser.getId());
        if (!isReporter) {
            // requireProjectLead will throw 403 if not LEAD/ADMIN/OWNER
            projectAuthorizationService.requireLead(workspaceId, projectId);
        }

        activityLogService.log(
            task.getProject().getWorkspace(),
            task.getProject(),
            null,
            currentUser,
            "TASK_DELETED",
            "TASK",
            task.getId(),
            Map.of("title", task.getTitle())
        );
        deleteAttachmentsForTask(taskId);
        taskRepository.delete(task);
    }

    public Task findTaskInProject(Long workspaceId, Long projectId, Long taskId) {
        return taskRepository.findById(taskId)
            .filter(task -> task.getProject().getId().equals(projectId))
            .filter(task -> task.getProject().getWorkspace().getId().equals(workspaceId))
            .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private User resolveAssignee(Long projectId, Long assigneeUserId) {
        if (assigneeUserId == null) {
            return null;
        }
        projectMemberRepository.findByProjectIdAndUserId(projectId, assigneeUserId)
            .filter(member -> member.getRole() != com.hoang.worknest.enums.ProjectRole.VIEWER)
            .orElseThrow(() -> new ResourceNotFoundException("Assignee must be a project member or lead"));
        return userRepository.findById(assigneeUserId)
            .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
            .orElseThrow(() -> new ResourceNotFoundException("Assignee user not found"));
    }

    private User requireCurrentUserEntity() {
        Long userId = currentUserService.getCurrentUser().id();
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private void applyTimeline(Task task) {
        if (task.getStatus() == TaskStatus.IN_PROGRESS && task.getStartedAt() == null) {
            task.setStartedAt(OffsetDateTime.now());
        }
        if (task.getStatus() == TaskStatus.DONE) {
            task.setCompletedAt(OffsetDateTime.now());
        } else {
            task.setCompletedAt(null);
        }
    }

    private void validatePageRequest(int page, int size, String sortBy) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("Unsupported task sort field: " + sortBy);
        }
    }

    @Transactional
    public AttachmentResponse uploadAttachment(Long taskId, MultipartFile file) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        
        Long workspaceId = task.getProject().getWorkspace().getId();
        Long projectId = task.getProject().getId();
        projectAuthorizationService.requireTaskWriter(workspaceId, projectId, task);
        
        User uploader = requireCurrentUserEntity();
        
        try {
            FileStorageService.StoredFile stored = fileStorageService.uploadAttachment(file, "tasks/" + taskId);
            
            Attachment attachment = Attachment.builder()
                .task(task)
                .uploadedBy(uploader)
                .fileName(safeDisplayName(file.getOriginalFilename()))
                .contentType(stored.contentType())
                .fileSize(stored.size())
                .bucketName(fileStorageService.getBucketName())
                .objectKey(stored.objectKey())
                .build();
                
            attachment = attachmentRepository.save(attachment);
            
            String url = fileStorageService.generatePresignedUrl(stored.objectKey(), Duration.ofMinutes(10));
            return new AttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                url,
                userMapper.toResponse(uploader),
                attachment.getCreatedAt()
            );
        } catch (IOException e) {
            throw new ServiceUnavailableException("Failed to upload attachment", e);
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachments(Long taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
            
        Long workspaceId = task.getProject().getWorkspace().getId();
        Long projectId = task.getProject().getId();
        projectAuthorizationService.requireAccess(workspaceId, projectId);
        
        return attachmentRepository.findByTaskId(taskId).stream().map(attachment -> {
            String url = fileStorageService.generatePresignedUrl(attachment.getObjectKey(), Duration.ofMinutes(10));
            return new AttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                url,
                userMapper.toResponse(attachment.getUploadedBy()),
                attachment.getCreatedAt()
            );
        }).toList();
    }

    @Transactional
    public void deleteAttachment(Long taskId, Long attachmentId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        Long workspaceId = task.getProject().getWorkspace().getId();
        Long projectId = task.getProject().getId();
        projectAuthorizationService.requireTaskWriter(workspaceId, projectId, task);

        Attachment attachment = attachmentRepository.findByIdAndTaskId(attachmentId, taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        fileStorageService.deleteObject(attachment.getObjectKey());
        attachmentRepository.delete(attachment);
    }

    private String safeDisplayName(String originalFilename) {
        String name = originalFilename == null ? "attachment" : originalFilename;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\u0000]", "").trim();
        if (name.isBlank()) return "attachment";
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private void deleteAttachmentsForTask(Long taskId) {
        List<Attachment> attachments = attachmentRepository.findByTaskId(taskId);
        for (Attachment attachment : attachments) {
            fileStorageService.deleteObject(attachment.getObjectKey());
        }
        attachmentRepository.deleteAll(attachments);
    }
}
