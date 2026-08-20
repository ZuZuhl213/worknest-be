package com.hoang.worknest.config;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hoang.worknest.scheduler.OverdueTaskJob;
import com.hoang.worknest.scheduler.StorageCleanupJob;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail overdueTaskJobDetail() {
        return JobBuilder.newJob(OverdueTaskJob.class)
            .withIdentity("overdueTaskJob")
            .storeDurably()
            .build();
    }

    @Bean
    public Trigger overdueTaskTrigger(
        JobDetail overdueTaskJobDetail,
        @Value("${app.scheduler.overdue-task.interval-ms:300000}") long intervalMs
    ) {
        return TriggerBuilder.newTrigger()
            .forJob(overdueTaskJobDetail)
            .withIdentity("overdueTaskTrigger")
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMilliseconds(intervalMs)
                .withMisfireHandlingInstructionNextWithExistingCount()
                .repeatForever())
            .build();
    }

    @Bean
    public JobDetail storageCleanupJobDetail() {
        return JobBuilder.newJob(StorageCleanupJob.class)
            .withIdentity("storageCleanupJob")
            .storeDurably()
            .build();
    }

    @Bean
    public Trigger storageCleanupTrigger(
        JobDetail storageCleanupJobDetail,
        @Value("${app.scheduler.storage-cleanup.interval-ms:60000}") long intervalMs
    ) {
        return TriggerBuilder.newTrigger()
            .forJob(storageCleanupJobDetail)
            .withIdentity("storageCleanupTrigger")
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMilliseconds(intervalMs)
                .withMisfireHandlingInstructionNextWithExistingCount()
                .repeatForever())
            .build();
    }
}
