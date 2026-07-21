package com.hoang.worknest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hoang.worknest.dto.user.DeactivateAccountRequest;
import com.hoang.worknest.dto.user.UserProfileUpdateRequest;
import com.hoang.worknest.dto.user.UserResponse;
import com.hoang.worknest.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateCurrentUser(request));
    }

    @PostMapping("/me/deactivate")
    public ResponseEntity<Void> deactivate(@Valid @RequestBody DeactivateAccountRequest request) {
        userService.deactivateCurrentUser(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<UserResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadAvatar(file));
    }

    @GetMapping("/{userId}/avatar")
    public ResponseEntity<Void> avatar(@PathVariable Long userId) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, userService.getAvatarDownloadUrl(userId))
            .build();
    }
}
