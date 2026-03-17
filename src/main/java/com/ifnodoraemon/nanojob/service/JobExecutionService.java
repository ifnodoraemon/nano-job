package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.handler.JobHandler;
import com.ifnodoraemon.nanojob.handler.JobHandlerRegistry;
import com.ifnodoraemon.nanojob.metrics.JobMetricsService;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.support.exception.RetryableJobExecutionException;
import com.ifnodoraemon.nanojob.support.tracing.TraceContext;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final BlockingQueue<QueuedJob> jobDispatchQueue;
    private final JobHandlerRegistry jobHandlerRegistry;
    private final JobRepository jobRepository;
    private final JobLifecycleService jobLifecycleService;
    private final JobLeaseHeartbeatService jobLeaseHeartbeatService;
    private final JobExecutionLogService jobExecutionLogService;
    private final JobMetricsService jobMetricsService;

    public JobExecutionService(
            @Qualifier("jobDispatchQueue") BlockingQueue<QueuedJob> jobDispatchQueue,
            JobHandlerRegistry jobHandlerRegistry,
            JobRepository jobRepository,
            JobLifecycleService jobLifecycleService,
            JobLeaseHeartbeatService jobLeaseHeartbeatService,
            JobExecutionLogService jobExecutionLogService,
            JobMetricsService jobMetricsService
    ) {
        this.jobDispatchQueue = jobDispatchQueue;
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobRepository = jobRepository;
        this.jobLifecycleService = jobLifecycleService;
        this.jobLeaseHeartbeatService = jobLeaseHeartbeatService;
        this.jobExecutionLogService = jobExecutionLogService;
        this.jobMetricsService = jobMetricsService;
    }

    public void submit(Long jobId) {
        QueuedJob queuedJob = new QueuedJob(jobId, TraceContext.currentOrCreate("exec"));
        if (!jobDispatchQueue.offer(queuedJob)) {
            handleRejectedSubmission(jobId, "Dispatch queue rejected job " + jobId);
        }
    }

    void process(QueuedJob queuedJob) {
        run(queuedJob.jobId());
    }

    private void run(Long jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job disappeared before execution, jobId={}", jobId);
            return;
        }

        JobHandler handler = jobHandlerRegistry.get(job.getType());
        var executionLog = jobExecutionLogService.start(job);
        jobMetricsService.recordExecutionStarted(job.getType());
        log.debug("Executing jobKey={} handler={} traceId={}",
                job.getJobKey(), handler.getClass().getSimpleName(), TraceContext.getTraceId());

        try (var ignored = jobLeaseHeartbeatService.start(job)) {
            handler.handle(job);
            jobLifecycleService.markSuccess(job.getId());
            jobExecutionLogService.markSuccess(executionLog.getId(), "Execution completed");
            jobMetricsService.recordExecutionSucceeded(job.getType());
        } catch (Exception exception) {
            log.warn("Job execution failed for jobKey={} traceId={}: {}",
                    job.getJobKey(), TraceContext.getTraceId(), exception.getMessage());
            jobLifecycleService.markFailure(job, exception);
            jobExecutionLogService.markFailure(executionLog.getId(), exception.getMessage());
            jobMetricsService.recordExecutionFailed(job.getType());
        }
    }

    private void handleRejectedSubmission(Long jobId, String message) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job disappeared before rejected submission handling, jobId={}", jobId);
            return;
        }

        var executionLog = jobExecutionLogService.start(job);
        String jobMessage = message + " " + job.getJobKey();
        log.warn("{} traceId={}", jobMessage, TraceContext.getTraceId());
        jobLifecycleService.markFailure(job, new RetryableJobExecutionException(jobMessage));
        jobExecutionLogService.markFailure(executionLog.getId(), message);
        jobMetricsService.recordExecutionRejected(job.getType());
        jobMetricsService.recordExecutionFailed(job.getType());
    }
}
