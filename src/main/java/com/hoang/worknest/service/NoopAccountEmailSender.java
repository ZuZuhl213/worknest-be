package com.hoang.worknest.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.hoang.worknest.entity.User;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class NoopAccountEmailSender implements AccountEmailSender {
    @Override
    public void sendPasswordReset(User user, String rawToken) {
        log.info("Password reset email suppressed for {}", user.getEmail());
    }

    @Override
    public void sendEmailVerification(User user, String rawToken) {
        log.info("Email verification email suppressed for {}", user.getEmail());
    }
}
