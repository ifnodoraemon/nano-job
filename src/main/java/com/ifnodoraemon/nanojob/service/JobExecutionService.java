package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.handler.JobHandler;
import com.ifnodoraemon.nanojob.handler.JobHandlerRegistry;
import com.ifnodoraemon.nanojob.metrics.JobMetricsService;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobHandlerRegistry jobHandlerRegistry;
    private final JobRepository jobRepository;
    private final JobLifecycleService jobLifecycleService;
    private final JobLeaseHeartbeatService jobLeaseHeartbeatService;
    private final JobExecutionLogService jobExecutionLogService;
    private final JobMetricsService jobMetricsService;
    private final JobOutboxService jobOutboxService;

    public JobExecutionService(
            JobHandlerRegistry jobHandlerRegistry,
            JobRepository jobRepository,
            JobLifecycleService jobLifecycleService,
            JobLeaseHeartbeatService jobLeaseHeartbeatService,
            JobExecutionLogService jobExecutionLogService,
            JobMetricsService jobMetricsService,
            JobOutboxService jobOutboxService
    ) {
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobRepository = jobRepository;
        this.jobLifecycleService = jobLifecycleService;
        this.jobLeaseHeartbeatService = jobLeaseHeartbeatService;
        this.jobExecutionLogService = jobExecutionLogService;
        this.jobMetricsService = jobMetricsService;
        this.jobOutboxService = jobOutboxService;
    }

    void process(QueuedJob queuedJob) {
        if (queuedJob.outboxEventId() != null && !jobOutboxService.tryStartProcessing(queuedJob.outboxEventId())) {
            log.debug("Skipped queued job because outbox event is no longer publishable, outboxEventId={}", queuedJob.outboxEventId());
            return;
        }
        run(queuedJob);
    }

    private void run(QueuedJob queuedJob) {
        Job job = jobRepository.findById(queuedJob.jobId()).orElse(null);
        if (job == null) {
            log.warn("Job disappeared before execution, jobId={}", queuedJob.jobId());
            if (queuedJob.outboxEventId() != null) {
                jobOutboxService.markDiscarded(queuedJob.outboxEventId(), "Job disappeared before execution");
            }
            return;
        }

        if (job.getStatus() != JobStatus.RUNNING
                || !queuedJob.executionToken().equals(job.getExecutionToken())) {
            log.debug("Discarded stale queued job jobKey={} queuedToken={} currentToken={}",
                    job.getJobKey(), queuedJob.executionToken(), job.getExecutionToken());
            if (queuedJob.outboxEventId() != null) {
                jobOutboxService.markDiscarded(
                        queuedJob.outboxEventId(),
                        "Stale dispatch token for job " + job.getJobKey()
                );
            }
            return;
        }

        JobHandler handler = jobHandlerRegistry.get(job.getType());
        var executionLog = jobExecutionLogService.start(job);
        jobMetricsService.recordExecutionStarted(job.getType());
        log.debug("Executing jobKey={} handler={} traceId={}",
                job.getJobKey(), handler.getClass().getSimpleName(), queuedJob.traceId());

        try (var ignored = jobLeaseHeartbeatService.start(job)) {
            handler.handle(job);
            jobLifecycleService.markSuccess(job.getId(), job.getExecutionToken());
            jobExecutionLogService.markSuccess(executionLog.getId(), "Execution completed");
            jobMetricsService.recordExecutionSucceeded(job.getType());
        } catch (Exception exception) {
            log.warn("Job execution failed for jobKey={} traceId={}: {}",
                    job.getJobKey(), queuedJob.traceId(), exception.getMessage());
            jobLifecycleService.markFailure(job, exception);
            jobExecutionLogService.markFailure(executionLog.getId(), exception.getMessage());
            jobMetricsService.recordExecutionFailed(job.getType());
        } finally {
            if (queuedJob.outboxEventId() != null) {
                jobOutboxService.markProcessed(queuedJob.outboxEventId());
            }
        }
    }
}
