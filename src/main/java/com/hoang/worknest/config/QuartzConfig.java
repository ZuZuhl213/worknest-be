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
                .repeatForever())
            .build();
    }
}
