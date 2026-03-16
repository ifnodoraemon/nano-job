package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobDispatchService {

    private static final Logger log = LoggerFactory.getLogger(JobDispatchService.class);

    private final JobRepository jobRepository;
    private final JobLifecycleService jobLifecycleService;
    private final JobExecutionService jobExecutionService;

    public JobDispatchService(
            JobRepository jobRepository,
            JobLifecycleService jobLifecycleService,
            JobExecutionService jobExecutionService
    ) {
        this.jobRepository = jobRepository;
        this.jobLifecycleService = jobLifecycleService;
        this.jobExecutionService = jobExecutionService;
    }

    public void dispatchDueJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<Job> dueJobs = new ArrayList<>();
        dueJobs.addAll(jobRepository.findTop100ByStatusAndExecuteAtLessThanEqualOrderByExecuteAtAsc(JobStatus.PENDING, now));
        dueJobs.addAll(jobRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(JobStatus.RETRY_WAIT, now));

        int dispatched = 0;
        for (Job job : dueJobs) {
            if (jobLifecycleService.tryClaim(job)) {
                jobExecutionService.submit(job.getId());
                dispatched++;
            }
        }
        log.debug("Scheduler tick at {}, dueJobs={}, dispatched={}", now, dueJobs.size(), dispatched);
    }
}
