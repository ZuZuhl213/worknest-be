package com.hoang.worknest.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.hoang.worknest.entity.AccountToken;
import com.hoang.worknest.enums.AccountTokenType;

import jakarta.persistence.LockModeType;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountToken> findByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(OffsetDateTime cutoff);

    void deleteByUserIdAndTypeAndUsedAtIsNull(Long userId, AccountTokenType type);
}
