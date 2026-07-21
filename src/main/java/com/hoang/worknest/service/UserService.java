package com.hoang.worknest.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hoang.worknest.dto.common.PagedResponse;
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
        if (user.getSystemRole() == SystemRole.ADMIN
            && userRepository.findBySystemRoleAndIsActiveTrueOrderById(SystemRole.ADMIN).size() <= 1) {
            throw new ConflictException("The last active system administrator cannot be deactivated");
        }
        deactivate(user, user);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> searchUsers(String search, Boolean active, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
        String normalizedSearch = normalizeSearch(search);
        Page<User> result = normalizedSearch == null
            ? userRepository.searchAll(active, PageRequest.of(page, size))
            : userRepository.searchByText(normalizedSearch, active, PageRequest.of(page, size));
        return new PagedResponse<>(result.getContent().stream().map(userMapper::toResponse).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
            result.isFirst(), result.isLast());
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
            if (target.getSystemRole() == SystemRole.ADMIN
                && userRepository.findBySystemRoleAndIsActiveTrueOrderById(SystemRole.ADMIN).size() <= 1) {
                throw new ConflictException("The last active system administrator cannot be disabled");
            }
            deactivate(actor, target);
        }
        return userMapper.toResponse(userRepository.save(target));
    }

    @Transactional
    public UserResponse uploadAvatar(MultipartFile file) {
        User user = currentUserEntity();
        try {
            String objectKey = fileStorageService.uploadAvatar(file, "avatars");
            user.setAvatarObjectKey(objectKey);
            user.setAvatarUrl("/api/users/" + user.getId() + "/avatar");
            return userMapper.toResponse(userRepository.save(user));
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
        return search == null || search.isBlank() ? null : search.trim();
    }
}
