package com.hoang.worknest.service;

import com.hoang.worknest.entity.User;

public interface AccountEmailSender {
    void sendPasswordReset(User user, String rawToken);

    void sendEmailVerification(User user, String rawToken);
}
