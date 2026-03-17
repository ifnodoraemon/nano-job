package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.metrics.JobMetricsService;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.ifnodoraemon.nanojob.support.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class JobDispatchService {

    private static final Logger log = LoggerFactory.getLogger(JobDispatchService.class);

    private final JobRepository jobRepository;
    private final NanoJobProperties nanoJobProperties;
    private final JobDispatchCapacityService jobDispatchCapacityService;
    private final JobLifecycleService jobLifecycleService;
    private final JobExecutionService jobExecutionService;
    private final JobMetricsService jobMetricsService;

    public JobDispatchService(
            JobRepository jobRepository,
            NanoJobProperties nanoJobProperties,
            JobDispatchCapacityService jobDispatchCapacityService,
            JobLifecycleService jobLifecycleService,
            JobExecutionService jobExecutionService,
            JobMetricsService jobMetricsService
    ) {
        this.jobRepository = jobRepository;
        this.nanoJobProperties = nanoJobProperties;
        this.jobDispatchCapacityService = jobDispatchCapacityService;
        this.jobLifecycleService = jobLifecycleService;
        this.jobExecutionService = jobExecutionService;
        this.jobMetricsService = jobMetricsService;
    }

    public void dispatchDueJobs() {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = nanoJobProperties.getScheduler().getBatchSize();
        PageRequest page = PageRequest.of(0, batchSize);
        List<Job> timedOutRunningJobs = jobRepository
                .findByStatusAndLeaseExpiresAtLessThanEqualOrderByLeaseExpiresAtAsc(JobStatus.RUNNING, now, page);
        for (Job job : timedOutRunningJobs) {
            jobLifecycleService.recoverTimedOutJob(job);
        }

        List<Job> dueJobs = new ArrayList<>();
        dueJobs.addAll(jobRepository.findByStatusAndExecuteAtLessThanEqualOrderByExecuteAtAsc(JobStatus.PENDING, now, page));
        dueJobs.addAll(jobRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(JobStatus.RETRY_WAIT, now, page));

        int dispatched = 0;
        int claimBudget = nanoJobProperties.getScheduler().isCapacityAwareDispatch()
                ? jobDispatchCapacityService.availableSubmissionSlots()
                : Integer.MAX_VALUE;
        for (Job job : dueJobs) {
            if (dispatched >= claimBudget) {
                jobMetricsService.recordDispatchThrottled(job.getType());
                continue;
            }
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
                dispatched,
                claimBudget
        );
    }
}
