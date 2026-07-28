package com.hoang.worknest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hoang.worknest.entity.User;
import com.hoang.worknest.exception.ResourceNotFoundException;
import com.hoang.worknest.repository.UserRepository;

class CurrentUserServiceTest {

    private UserRepository userRepository;
    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        currentUserService = new CurrentUserService(userRepository);
        SecurityContextHolder.clearContext();
    }

    @Test
    void throwsWhenNoAuthenticatedUserPresent() {
        SecurityContextHolder.getContext().setAuthentication(null);

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
            currentUserService::getCurrentUser);

        assertEquals("Authenticated user not found", exception.getMessage());
    }

    @Test
    void throwsWhenAuthenticationIsAnonymous() {
        Authentication authentication = new AnonymousAuthenticationToken(
            "key",
            "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
            currentUserService::getCurrentUser);

        assertEquals("Authenticated user not found", exception.getMessage());
    }

    @Test
    void returnsAuthenticatedUserWhenUserExists() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("user@example.com");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = User.builder()
            .id(7L)
            .email("user@example.com")
            .fullName("Ada Lovelace")
            .emailVerified(true)
            .isActive(true)
            .systemRole(com.hoang.worknest.enums.SystemRole.USER)
            .tokenVersion(2)
            .lastLoginAt(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
            .build();

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        AuthenticatedUser result = currentUserService.getCurrentUser();

        assertEquals(7L, result.id());
        assertEquals("user@example.com", result.email());
        assertEquals("Ada Lovelace", result.fullName());
        assertEquals(true, result.emailVerified());
        assertEquals(true, result.isActive());
        assertEquals(com.hoang.worknest.enums.SystemRole.USER, result.systemRole());
        assertEquals(2, result.tokenVersion());
        assertEquals(OffsetDateTime.parse("2024-01-01T00:00:00Z"), result.lastLoginAt());
        verify(userRepository).findByEmailIgnoreCase("user@example.com");
    }
}
