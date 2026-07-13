package com.hoang.worknest.service;

import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.config.CacheConfig;
import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.task.TaskAssignRequest;
import com.hoang.worknest.dto.task.TaskCreateRequest;
import com.hoang.worknest.dto.task.TaskResponse;
import com.hoang.worknest.dto.task.TaskUpdateRequest;
import com.hoang.worknest.entity.Project;
import com.hoang.worknest.entity.Task;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.enums.TaskPriority;
import com.hoang.worknest.enums.TaskStatus;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.TaskMapper;
import com.hoang.worknest.repository.TaskRepository;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.repository.WorkspaceMemberRepository;
import com.hoang.worknest.repository.specification.TaskSpecifications;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.WorkspaceAccessService;

import com.hoang.worknest.entity.Attachment;
import com.hoang.worknest.repository.AttachmentRepository;
import com.hoang.worknest.dto.task.AttachmentResponse;
import com.hoang.worknest.service.FileStorageService;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Duration;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "createdAt", "updatedAt", "dueDate", "priority", "status", "taskNumber", "title"
    );

    private final TaskRepository taskRepository;
    private final ProjectService projectService;
    private final WorkspaceAccessService workspaceAccessService;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final TaskMapper taskMapper;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TASKS_BY_PROJECT, allEntries = true)
    public TaskResponse create(Long workspaceId, Long projectId, TaskCreateRequest request) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        Project project = projectService.findProjectInWorkspace(workspaceId, projectId);
        User reporter = requireCurrentUserEntity();
        User assignee = resolveAssignee(workspaceId, request.assigneeUserId());

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
            "{\"status\":\"" + savedTask.getStatus() + "\"}"
        );
        return taskMapper.toResponse(savedTask);
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = CacheConfig.TASKS_BY_PROJECT,
        key = "#workspaceId + ':' + #projectId + ':' + @currentUserService.getCurrentUser().id() + ':' + #status + ':' + #priority + ':' + #assigneeId + ':' + #search + ':' + #dueFrom + ':' + #dueTo + ':' + #page + ':' + #size + ':' + #sortBy + ':' + #sortDirection"
    )
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
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        projectService.findProjectInWorkspace(workspaceId, projectId);
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
    @Cacheable(cacheNames = CacheConfig.TASK_DETAIL, key = "#workspaceId + ':' + #projectId + ':' + #taskId + ':' + @currentUserService.getCurrentUser().id()")
    public TaskResponse getById(Long workspaceId, Long projectId, Long taskId) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        Task task = findTaskInProject(workspaceId, projectId, taskId);
        return taskMapper.toResponse(task);
    }

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.TASKS_BY_PROJECT,
            CacheConfig.TASK_DETAIL
        },
        allEntries = true
    )
    public TaskResponse update(Long workspaceId, Long projectId, Long taskId, TaskUpdateRequest request) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        Task task = findTaskInProject(workspaceId, projectId, taskId);
        User actor = requireCurrentUserEntity();
        User assignee = resolveAssignee(workspaceId, request.assigneeUserId());
        Long oldAssigneeId = task.getAssignee() != null ? task.getAssignee().getId() : null;

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
            "{\"status\":\"" + savedTask.getStatus() + "\",\"priority\":\"" + savedTask.getPriority() + "\"}"
        );
        return taskMapper.toResponse(savedTask);
    }

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.TASKS_BY_PROJECT,
            CacheConfig.TASK_DETAIL
        },
        allEntries = true
    )
    public TaskResponse assign(Long workspaceId, Long projectId, Long taskId, TaskAssignRequest request) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        Task task = findTaskInProject(workspaceId, projectId, taskId);
        User actor = requireCurrentUserEntity();
        User assignee = resolveAssignee(workspaceId, request.assigneeUserId());
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
            "{\"assigneeUserId\":" + assignee.getId() + "}"
        );
        return taskMapper.toResponse(savedTask);
    }

    @Transactional
    @CacheEvict(
        cacheNames = {
            CacheConfig.TASKS_BY_PROJECT,
            CacheConfig.TASK_DETAIL
        },
        allEntries = true
    )
    public void delete(Long workspaceId, Long projectId, Long taskId) {
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        Task task = findTaskInProject(workspaceId, projectId, taskId);
        activityLogService.log(
            task.getProject().getWorkspace(),
            task.getProject(),
            task,
            requireCurrentUserEntity(),
            "TASK_DELETED",
            "TASK",
            task.getId(),
            "{\"title\":\"" + task.getTitle() + "\"}"
        );
        taskRepository.delete(task);
    }

    public Task findTaskInProject(Long workspaceId, Long projectId, Long taskId) {
        return taskRepository.findById(taskId)
            .filter(task -> task.getProject().getId().equals(projectId))
            .filter(task -> task.getProject().getWorkspace().getId().equals(workspaceId))
            .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private User resolveAssignee(Long workspaceId, Long assigneeUserId) {
        if (assigneeUserId == null) {
            return null;
        }
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, assigneeUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignee is not a member of this workspace"));
        return userRepository.findById(assigneeUserId)
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
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        
        User uploader = requireCurrentUserEntity();
        
        try {
            String objectKey = fileStorageService.uploadFile(file, "tasks/" + taskId);
            
            Attachment attachment = Attachment.builder()
                .task(task)
                .uploadedBy(uploader)
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .bucketName(fileStorageService.getBucketName())
                .objectKey(objectKey)
                .build();
                
            attachment = attachmentRepository.save(attachment);
            
            String url = fileStorageService.generatePresignedUrl(objectKey, Duration.ofMinutes(60));
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
            throw new RuntimeException("Failed to upload attachment", e);
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachments(Long taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
            
        Long workspaceId = task.getProject().getWorkspace().getId();
        workspaceAccessService.requireWorkspaceMember(workspaceId);
        
        return attachmentRepository.findByTaskId(taskId).stream().map(attachment -> {
            String url = fileStorageService.generatePresignedUrl(attachment.getObjectKey(), Duration.ofMinutes(60));
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
}
