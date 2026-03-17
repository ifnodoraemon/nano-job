package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.metrics.JobMetricsService;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import com.ifnodoraemon.nanojob.support.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobDispatchService {

    private static final Logger log = LoggerFactory.getLogger(JobDispatchService.class);

    private final JobRepository jobRepository;
    private final JobLifecycleService jobLifecycleService;
    private final JobExecutionService jobExecutionService;
    private final JobMetricsService jobMetricsService;

    public JobDispatchService(
            JobRepository jobRepository,
            JobLifecycleService jobLifecycleService,
            JobExecutionService jobExecutionService,
            JobMetricsService jobMetricsService
    ) {
        this.jobRepository = jobRepository;
        this.jobLifecycleService = jobLifecycleService;
        this.jobExecutionService = jobExecutionService;
        this.jobMetricsService = jobMetricsService;
    }

    public void dispatchDueJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<Job> timedOutRunningJobs = jobRepository
                .findTop100ByStatusAndLeaseExpiresAtLessThanEqualOrderByLeaseExpiresAtAsc(JobStatus.RUNNING, now);
        for (Job job : timedOutRunningJobs) {
            jobLifecycleService.recoverTimedOutJob(job);
        }

        List<Job> dueJobs = new ArrayList<>();
        dueJobs.addAll(jobRepository.findTop100ByStatusAndExecuteAtLessThanEqualOrderByExecuteAtAsc(JobStatus.PENDING, now));
        dueJobs.addAll(jobRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(JobStatus.RETRY_WAIT, now));

        int dispatched = 0;
        for (Job job : dueJobs) {
            if (jobLifecycleService.tryClaim(job)) {
                jobExecutionService.submit(job.getId());
                jobMetricsService.recordDispatchClaimed(job.getType());
                dispatched++;
            }
        }
        log.debug(
                "Scheduler tick at {} traceId={} timedOutRunningJobs={} dueJobs={} dispatched={}",
                now,
                TraceContext.getTraceId(),
                timedOutRunningJobs.size(),
                dueJobs.size(),
                dispatched
        );
    }
}
