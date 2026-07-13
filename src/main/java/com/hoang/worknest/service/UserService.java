package com.hoang.worknest.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.hoang.worknest.dto.user.UserCreateRequest;
import com.hoang.worknest.dto.user.UserResponse;
import com.hoang.worknest.entity.User;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.mapper.UserMapper;
import com.hoang.worknest.repository.UserRepository;

import org.springframework.web.multipart.MultipartFile;
import com.hoang.worknest.security.CurrentUserService;
import com.hoang.worknest.security.AuthenticatedUser;
import java.io.IOException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return userMapper.toResponse(userRepository.save(user));
    }

    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
            .map(userMapper::toResponse)
            .toList();
    }

    public UserResponse getById(Long id) {
        return userRepository.findById(id)
            .map(userMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found");
        }
        userRepository.deleteById(id);
    }

    public UserResponse uploadAvatar(MultipartFile file) {
        try {
            AuthenticatedUser currentUser = currentUserService.getCurrentUser();
            User user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            
            String objectKey = fileStorageService.uploadFile(file, "avatars");
            String publicUrl = fileStorageService.getPublicUrl(objectKey);
            
            user.setAvatarUrl(publicUrl);
            return userMapper.toResponse(userRepository.save(user));
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload avatar", e);
        }
    }
}
