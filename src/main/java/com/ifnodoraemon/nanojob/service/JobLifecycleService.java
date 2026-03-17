package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.metrics.JobMetricsService;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.retry.RetryDecision;
import com.ifnodoraemon.nanojob.retry.RetryPolicyRegistry;
import com.ifnodoraemon.nanojob.support.exception.InvalidJobStateException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobLifecycleService {

    private final JobRepository jobRepository;
    private final NanoJobProperties properties;
    private final JobExecutionLogService jobExecutionLogService;
    private final RetryPolicyRegistry retryPolicyRegistry;
    private final JobMetricsService jobMetricsService;

    public JobLifecycleService(
            JobRepository jobRepository,
            NanoJobProperties properties,
            JobExecutionLogService jobExecutionLogService,
            RetryPolicyRegistry retryPolicyRegistry,
            JobMetricsService jobMetricsService
    ) {
        this.jobRepository = jobRepository;
        this.properties = properties;
        this.jobExecutionLogService = jobExecutionLogService;
        this.retryPolicyRegistry = retryPolicyRegistry;
        this.jobMetricsService = jobMetricsService;
    }

    @Transactional
    public boolean tryClaim(Job job) {
        // Use a conditional update as a lightweight compare-and-set so the same
        // job cannot be claimed twice when scheduler ticks overlap.
        LocalDateTime now = LocalDateTime.now();
        return jobRepository.claimForExecution(
                job.getId(),
                EnumSet.of(JobStatus.PENDING, JobStatus.RETRY_WAIT),
                JobStatus.RUNNING,
                properties.getExecution().getWorkerId(),
                now.plus(properties.getExecution().getLeaseDuration()),
                now
        ) == 1;
    }

    @Transactional
    public boolean renewLease(Long jobId) {
        LocalDateTime now = LocalDateTime.now();
        return jobRepository.renewLease(
                jobId,
                JobStatus.RUNNING,
                properties.getExecution().getWorkerId(),
                now.plus(properties.getExecution().getLeaseDuration()),
                now
        ) == 1;
    }

    @Transactional
    public void markSuccess(Long jobId) {
        int updated = jobRepository.markSuccess(jobId, JobStatus.RUNNING, JobStatus.SUCCESS, LocalDateTime.now());
        if (updated != 1) {
            throw new InvalidJobStateException("Job was not RUNNING when marking success: " + jobId);
        }
    }

    @Transactional
    public void markFailure(Job job, Exception exception) {
        RetryDecision decision = retryPolicyRegistry.get(job.getType()).evaluate(job, exception);
        LocalDateTime now = LocalDateTime.now();

        if (decision.retryable()) {
            int updated = jobRepository.markRetryWaiting(
                    job.getId(),
                    JobStatus.RUNNING,
                    JobStatus.RETRY_WAIT,
                    decision.nextRetryCount(),
                    decision.nextRetryAt(),
                    decision.reason(),
                    now
            );
            if (updated != 1) {
                throw new InvalidJobStateException("Job was not RUNNING when marking retry wait: " + job.getId());
            }
            jobMetricsService.recordRetryScheduled(job.getType());
            return;
        }

        int updated = jobRepository.markFailed(
                job.getId(),
                JobStatus.RUNNING,
                JobStatus.FAILED,
                decision.nextRetryCount(),
                decision.reason(),
                now
        );
        if (updated != 1) {
            throw new InvalidJobStateException("Job was not RUNNING when marking failed: " + job.getId());
        }
        jobMetricsService.recordFinalFailure(job.getType());
    }

    @Transactional
    public boolean recoverTimedOutJob(Job job) {
        String errorMessage = "Execution lease expired for owner: " + job.getLockOwner();
        RetryDecision decision = retryPolicyRegistry.get(job.getType()).evaluate(job, new IllegalStateException(errorMessage));
        LocalDateTime now = LocalDateTime.now();
        int updated = decision.retryable()
                ? jobRepository.recoverTimedOutToRetryWaiting(
                        job.getId(),
                        JobStatus.RUNNING,
                        job.getLockOwner(),
                        job.getLeaseExpiresAt(),
                        JobStatus.RETRY_WAIT,
                        decision.nextRetryCount(),
                        decision.nextRetryAt(),
                        decision.reason(),
                        now
                )
                : jobRepository.recoverTimedOutToFailed(
                        job.getId(),
                        JobStatus.RUNNING,
                        job.getLockOwner(),
                        job.getLeaseExpiresAt(),
                        JobStatus.FAILED,
                        decision.nextRetryCount(),
                        decision.reason(),
                        now
                );
        if (updated != 1) {
            return false;
        }

        if (decision.retryable()) {
            jobMetricsService.recordRetryScheduled(job.getType());
        } else {
            jobMetricsService.recordFinalFailure(job.getType());
        }
        jobExecutionLogService.markLatestRunningAsFailed(job.getId(), errorMessage);
        jobMetricsService.recordLeaseRecovered(job.getType());
        return true;
    }
}
