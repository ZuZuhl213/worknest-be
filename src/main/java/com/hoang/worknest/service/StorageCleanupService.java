package com.hoang.worknest.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hoang.worknest.entity.StorageCleanupJob;
import com.hoang.worknest.repository.StorageCleanupJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageCleanupService {
    private final StorageCleanupJobRepository repository;
    private final FileStorageService fileStorageService;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.storage-cleanup.grace-period-ms:86400000}")
    private long gracePeriodMs;

    @Transactional
    public void enqueue(String bucketName, String objectKey) {
        repository.findByBucketNameAndObjectKey(bucketName, objectKey).orElseGet(() ->
            repository.save(StorageCleanupJob.builder()
                .bucketName(bucketName)
                .objectKey(objectKey)
                .nextAttemptAt(OffsetDateTime.now())
                .build())
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueProvisional(String bucketName, String objectKey) {
        repository.findByBucketNameAndObjectKey(bucketName, objectKey).orElseGet(() ->
            repository.save(StorageCleanupJob.builder()
                .bucketName(bucketName)
                .objectKey(objectKey)
                .nextAttemptAt(OffsetDateTime.now().plusNanos(gracePeriodMs * 1_000_000L))
                .build())
        );
    }

    @Transactional
    public void retain(String bucketName, String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Storage retention requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    repository.findByBucketNameAndObjectKey(bucketName, objectKey).ifPresent(repository::delete)
                );
            }
        });
    }

    @Transactional
    public void processDueJobs() {
        List<StorageCleanupJob> jobs = repository
            .findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(OffsetDateTime.now());
        for (StorageCleanupJob job : jobs) {
            try {
                fileStorageService.deleteObject(job.getBucketName(), job.getObjectKey());
                repository.delete(job);
            } catch (RuntimeException ex) {
                int attempts = job.getAttemptCount() + 1;
                long delaySeconds = Math.min(3600L, 1L << Math.min(attempts, 12));
                job.setAttemptCount(attempts);
                job.setLastError(ex.getMessage());
                job.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delaySeconds));
                log.warn("Storage cleanup failed for {}/{}", job.getBucketName(), job.getObjectKey(), ex);
            }
        }
    }
}
