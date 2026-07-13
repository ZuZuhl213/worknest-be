package com.hoang.worknest.scheduler;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import com.hoang.worknest.service.OverdueTaskService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OverdueTaskJob extends QuartzJobBean {

    private final OverdueTaskService overdueTaskService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        overdueTaskService.notifyOverdueTasks();
    }
}
