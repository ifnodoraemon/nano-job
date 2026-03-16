package com.ifnodoraemon.nanojob.scheduler;

import com.ifnodoraemon.nanojob.service.JobDispatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobScheduler {

    private final JobDispatchService jobDispatchService;

    public JobScheduler(JobDispatchService jobDispatchService) {
        this.jobDispatchService = jobDispatchService;
    }

    @Scheduled(fixedDelayString = "${nano-job.scheduler.poll-interval:1s}")
    public void poll() {
        jobDispatchService.dispatchDueJobs();
    }
}
