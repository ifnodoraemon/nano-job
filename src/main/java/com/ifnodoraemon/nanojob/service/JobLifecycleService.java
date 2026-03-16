package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.support.exception.InvalidJobStateException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobLifecycleService {

    private final JobRepository jobRepository;
    private final NanoJobProperties properties;

    public JobLifecycleService(JobRepository jobRepository, NanoJobProperties properties) {
        this.jobRepository = jobRepository;
        this.properties = properties;
    }

    @Transactional
    public boolean tryClaim(Job job) {
        // Use a conditional update as a lightweight compare-and-set so the same
        // job cannot be claimed twice when scheduler ticks overlap.
        return jobRepository.claimForExecution(
                job.getId(),
                EnumSet.of(JobStatus.PENDING, JobStatus.RETRY_WAIT),
                JobStatus.RUNNING,
                LocalDateTime.now()
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
        int nextRetryCount = job.getRetryCount() + 1;
        String errorMessage = buildErrorMessage(exception);
        LocalDateTime now = LocalDateTime.now();

        if (nextRetryCount <= job.getMaxRetry()) {
            int updated = jobRepository.markRetryWaiting(
                    job.getId(),
                    JobStatus.RUNNING,
                    JobStatus.RETRY_WAIT,
                    nextRetryCount,
                    now.plus(properties.getExecution().getRetryDelay()),
                    errorMessage,
                    now
            );
            if (updated != 1) {
                throw new InvalidJobStateException("Job was not RUNNING when marking retry wait: " + job.getId());
            }
            return;
        }

        int updated = jobRepository.markFailed(
                job.getId(),
                JobStatus.RUNNING,
                JobStatus.FAILED,
                nextRetryCount,
                errorMessage,
                now
        );
        if (updated != 1) {
            throw new InvalidJobStateException("Job was not RUNNING when marking failed: " + job.getId());
        }
    }

    private String buildErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
