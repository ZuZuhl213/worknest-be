package com.hoang.worknest.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hoang.worknest.dto.common.PagedResponse;
import com.hoang.worknest.dto.user.AdminOverviewResponse;
import com.hoang.worknest.dto.user.DeactivateAccountRequest;
import com.hoang.worknest.dto.user.UserProfileUpdateRequest;
import com.hoang.worknest.dto.user.UserResponse;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.enums.SystemRole;
import com.hoang.worknest.exception.ConflictException;
import com.hoang.worknest.exception.ForbiddenException;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.UserMapper;
import com.hoang.worknest.repository.UserRepository;
import com.hoang.worknest.repository.WorkspaceRepository;
import com.hoang.worknest.security.CurrentUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final StorageCleanupService storageCleanupService;
    private final CurrentUserService currentUserService;
    private final AuthService authService;
    private final SecurityAuditService auditService;

    @Transactional
    public UserResponse updateCurrentUser(UserProfileUpdateRequest request) {
        User user = currentUserEntity();
        user.setFullName(request.fullName().trim());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void deactivateCurrentUser(DeactivateAccountRequest request) {
        User user = currentUserEntity();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ForbiddenException("Current password is invalid");
        }
        if (workspaceRepository.existsByOwnerId(user.getId())) {
            throw new ConflictException("Transfer owned workspaces before deactivating this account");
        }
        if (user.getSystemRole() == SystemRole.SYSTEM_ADMIN
            && userRepository.findBySystemRoleAndIsActiveTrueOrderById(SystemRole.SYSTEM_ADMIN).size() <= 1) {
            throw new ConflictException("The last active system administrator cannot be deactivated");
        }
        deactivate(user, user);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> searchUsers(
        String search, Boolean active, Boolean emailVerified, SystemRole role,
        int page, int size, String sort, String direction
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
        String normalizedSearch = normalizeSearch(search);
        Page<User> result = userRepository.search(normalizedSearch, active, emailVerified, role,
            PageRequest.of(page, size, Sort.by(parseDirection(direction), parseSort(sort))));
        return new PagedResponse<>(result.getContent().stream().map(userMapper::toResponse).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
            result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getAdminOverview() {
        Page<User> recent = userRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new AdminOverviewResponse(
            userRepository.count(),
            userRepository.countByIsActiveTrue(),
            userRepository.countByIsActiveFalse(),
            userRepository.countByEmailVerifiedTrue(),
            recent.getContent().stream().map(userMapper::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUserForAdmin(Long id) {
        return userMapper.toResponse(requireUser(id));
    }

    @Transactional
    public UserResponse setUserActive(Long id, boolean active) {
        User actor = currentUserEntity();
        User target = requireUser(id);
        if (active) {
            target.setIsActive(true);
            target.setDeactivatedAt(null);
            auditService.log(actor, target, "ACCOUNT_ENABLED", "SUCCESS", Map.of());
        } else {
            if (target.getId().equals(actor.getId())) {
                throw new ForbiddenException("System administrators cannot disable their own account");
            }
            if (target.getSystemRole() == SystemRole.SYSTEM_ADMIN
                && userRepository.findBySystemRoleAndIsActiveTrueOrderById(SystemRole.SYSTEM_ADMIN).size() <= 1) {
                throw new ConflictException("The last active system administrator cannot be disabled");
            }
            deactivate(actor, target);
        }
        return userMapper.toResponse(userRepository.save(target));
    }

    @Transactional
    public UserResponse setWorkspaceCreation(Long id, boolean enabled) {
        User actor = currentUserEntity();
        User target = requireUser(id);
        target.setCanCreateWorkspace(enabled);
        User saved = userRepository.save(target);
        auditService.log(actor, saved,
            enabled ? "WORKSPACE_CREATION_ENABLED" : "WORKSPACE_CREATION_DISABLED", "SUCCESS", Map.of());
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse uploadAvatar(MultipartFile file) {
        User user = currentUserEntity();
        String previousObjectKey = user.getAvatarObjectKey();
        try {
            String objectKey = fileStorageService.uploadAvatar(file, "avatars");
            String bucketName = fileStorageService.getBucketName();
            storageCleanupService.enqueueProvisional(bucketName, objectKey);
            user.setAvatarObjectKey(objectKey);
            user.setAvatarUrl("/api/users/" + user.getId() + "/avatar");
            User saved = userRepository.save(user);
            storageCleanupService.retain(bucketName, objectKey);
            if (previousObjectKey != null && !previousObjectKey.isBlank() && !previousObjectKey.equals(objectKey)) {
                storageCleanupService.enqueue(bucketName, previousObjectKey);
            }
            return userMapper.toResponse(saved);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid avatar upload");
        }
    }

    @Transactional(readOnly = true)
    public String getAvatarDownloadUrl(Long userId) {
        User user = requireUser(userId);
        if (user.getAvatarObjectKey() == null || user.getAvatarObjectKey().isBlank()) {
            throw new ResourceNotFoundException("Avatar not found");
        }
        return fileStorageService.generateAvatarPresignedUrl(user.getAvatarObjectKey());
    }

    private void deactivate(User actor, User target) {
        target.setIsActive(false);
        target.setDeactivatedAt(OffsetDateTime.now());
        target.setTokenVersion(target.getTokenVersion() + 1);
        userRepository.save(target);
        authService.revokeAllForUser(target.getId());
        auditService.log(actor, target, "ACCOUNT_DISABLED", "SUCCESS", Map.of());
    }

    private User currentUserEntity() {
        return requireUser(currentUserService.getCurrentUser().id());
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank() ? "" : search.trim();
    }

    private String parseSort(String sort) {
        return switch (sort == null || sort.isBlank() ? "createdAt" : sort) {
            case "createdAt" -> "createdAt";
            case "lastLoginAt" -> "lastLoginAt";
            case "fullName" -> "fullName";
            default -> throw new IllegalArgumentException("Invalid sort field");
        };
    }

    private Sort.Direction parseDirection(String direction) {
        if (direction == null || direction.isBlank() || direction.equalsIgnoreCase("desc")) {
            return Sort.Direction.DESC;
        }
        if (direction.equalsIgnoreCase("asc")) {
            return Sort.Direction.ASC;
        }
        throw new IllegalArgumentException("Invalid sort direction");
    }
}
