package com.hoang.worknest.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.hoang.worknest.entity.User;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        return new AuthenticatedUser(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getEmailVerified(),
            user.getIsActive(),
            user.getSystemRole(),
            user.getCanCreateWorkspace(),
            user.getTokenVersion(),
            user.getLastLoginAt()
        );
    }
}
