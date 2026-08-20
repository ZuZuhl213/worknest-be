package com.hoang.worknest.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import com.hoang.worknest.service.StorageCleanupService;

import lombok.RequiredArgsConstructor;

@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class StorageCleanupJob extends QuartzJobBean {
    private final StorageCleanupService storageCleanupService;

    @Override
    protected void executeInternal(@NonNull JobExecutionContext context) throws JobExecutionException {
        storageCleanupService.processDueJobs();
    }
}
