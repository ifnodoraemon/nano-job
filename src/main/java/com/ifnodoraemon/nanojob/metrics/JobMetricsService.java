package com.ifnodoraemon.nanojob.metrics;

import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class JobMetricsService {

    private final MeterRegistry meterRegistry;
    private final Map<JobStatus, Gauge> statusGauges = new EnumMap<>(JobStatus.class);

    public JobMetricsService(
            MeterRegistry meterRegistry,
            JobRepository jobRepository,
            @Qualifier("jobTaskExecutor") ThreadPoolTaskExecutor jobTaskExecutor
    ) {
        this.meterRegistry = meterRegistry;
        for (JobStatus status : JobStatus.values()) {
            Gauge gauge = Gauge.builder("nano.job.status.count", jobRepository, repo -> repo.countByStatus(status))
                    .description("Current number of jobs by status")
                    .tag("status", status.name())
                    .register(meterRegistry);
            statusGauges.put(status, gauge);
        }

        Gauge.builder("nano.job.executor.active.count", jobTaskExecutor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Current active nano-job worker threads")
                .register(meterRegistry);
        Gauge.builder("nano.job.executor.queue.size", jobTaskExecutor,
                        executor -> executor.getThreadPoolExecutor() == null ? 0 : executor.getThreadPoolExecutor().getQueue().size())
                .description("Current nano-job executor queue size")
                .register(meterRegistry);
        Gauge.builder("nano.job.dispatch.available_slots", jobTaskExecutor, executor -> {
                    if (executor.getThreadPoolExecutor() == null) {
                        return 0;
                    }
                    int totalCapacity = executor.getMaxPoolSize() + executor.getThreadPoolExecutor().getQueue().remainingCapacity();
                    int inFlight = executor.getActiveCount() + executor.getThreadPoolExecutor().getQueue().size();
                    return Math.max(0, totalCapacity - inFlight);
                })
                .description("Estimated available nano-job dispatch slots")
                .register(meterRegistry);
    }

    public void recordExecutionStarted(JobType type) {
        counter("nano.job.execution.started", type).increment();
    }

    public void recordExecutionSucceeded(JobType type) {
        counter("nano.job.execution.succeeded", type).increment();
    }

    public void recordExecutionFailed(JobType type) {
        counter("nano.job.execution.failed", type).increment();
    }

    public void recordRetryScheduled(JobType type) {
        counter("nano.job.execution.retry.scheduled", type).increment();
    }

    public void recordFinalFailure(JobType type) {
        counter("nano.job.execution.final_failed", type).increment();
    }

    public void recordLeaseRecovered(JobType type) {
        counter("nano.job.execution.lease_recovered", type).increment();
    }

    public void recordLeaseRenewed(JobType type) {
        counter("nano.job.execution.lease_renewed", type).increment();
    }

    public void recordDispatchClaimed(JobType type) {
        counter("nano.job.dispatch.claimed", type).increment();
    }

    public void recordDispatchThrottled(JobType type) {
        counter("nano.job.dispatch.throttled", type).increment();
    }

    public void recordExecutionRejected(JobType type) {
        counter("nano.job.execution.rejected", type).increment();
    }

    private Counter counter(String name, JobType type) {
        return Counter.builder(name)
                .description("nano-job execution metric")
                .tags(Tags.of("type", type.name()))
                .register(meterRegistry);
    }
}
