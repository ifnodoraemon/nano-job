package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.handler.JobHandler;
import com.ifnodoraemon.nanojob.handler.JobHandlerRegistry;
import com.ifnodoraemon.nanojob.metrics.JobMetricsService;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final TaskExecutor jobTaskExecutor;
    private final JobHandlerRegistry jobHandlerRegistry;
    private final JobRepository jobRepository;
    private final JobLifecycleService jobLifecycleService;
    private final JobExecutionLogService jobExecutionLogService;
    private final JobMetricsService jobMetricsService;

    public JobExecutionService(
            @Qualifier("jobTaskExecutor") TaskExecutor jobTaskExecutor,
            JobHandlerRegistry jobHandlerRegistry,
            JobRepository jobRepository,
            JobLifecycleService jobLifecycleService,
            JobExecutionLogService jobExecutionLogService,
            JobMetricsService jobMetricsService
    ) {
        this.jobTaskExecutor = jobTaskExecutor;
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobRepository = jobRepository;
        this.jobLifecycleService = jobLifecycleService;
        this.jobExecutionLogService = jobExecutionLogService;
        this.jobMetricsService = jobMetricsService;
    }

    public void submit(Long jobId) {
        jobTaskExecutor.execute(() -> run(jobId));
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
        log.debug("Executing jobKey={} with handler={}", job.getJobKey(), handler.getClass().getSimpleName());

        try {
            handler.handle(job);
            jobLifecycleService.markSuccess(job.getId());
            jobExecutionLogService.markSuccess(executionLog.getId(), "Execution completed");
            jobMetricsService.recordExecutionSucceeded(job.getType());
        } catch (Exception exception) {
            log.warn("Job execution failed for jobKey={}: {}", job.getJobKey(), exception.getMessage());
            jobLifecycleService.markFailure(job, exception);
            jobExecutionLogService.markFailure(executionLog.getId(), exception.getMessage());
            jobMetricsService.recordExecutionFailed(job.getType());
        }
    }
}
