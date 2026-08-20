package com.hoang.worknest.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hoang.worknest.entity.StorageCleanupJob;

public interface StorageCleanupJobRepository extends JpaRepository<StorageCleanupJob, Long> {
    Optional<StorageCleanupJob> findByBucketNameAndObjectKey(String bucketName, String objectKey);
    List<StorageCleanupJob> findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(OffsetDateTime now);
}
