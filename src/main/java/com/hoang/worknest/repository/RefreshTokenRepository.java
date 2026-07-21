package com.hoang.worknest.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.hoang.worknest.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    Optional<RefreshToken> findFirstByTokenHash(String tokenHash);
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
    List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(java.util.UUID familyId);
    void deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
