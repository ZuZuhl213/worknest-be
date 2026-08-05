package com.hoang.worknest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.hoang.worknest.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class SmtpAccountEmailSender implements AccountEmailSender {
    private final JavaMailSender mailSender;

    @Value("${app.email.from:no-reply@worknest.local}")
    private String from;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Override
    public void sendPasswordReset(User user, String rawToken) {
        send(
            user,
            "Reset your WorkNest password",
            "Use this link to reset your password: " + frontendBaseUrl + "/reset-password?token=" + rawToken
        );
    }

    @Override
    public void sendEmailVerification(User user, String rawToken) {
        send(
            user,
            "Verify your WorkNest email",
            "Use this link to verify your email: " + frontendBaseUrl + "/verify-email?token=" + rawToken
        );
    }

    private void send(User user, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
