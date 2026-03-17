package com.ifnodoraemon.nanojob.metrics;

import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.domain.enums.OutboxStatus;
import com.ifnodoraemon.nanojob.repository.JobOutboxEventRepository;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.service.JobDispatchCapacityService;
import com.ifnodoraemon.nanojob.service.JobWorkerService;
import com.ifnodoraemon.nanojob.service.QueuedJob;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.BlockingQueue;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class JobMetricsService {

    private final MeterRegistry meterRegistry;
    private final Map<JobStatus, Gauge> statusGauges = new EnumMap<>(JobStatus.class);

    public JobMetricsService(
            MeterRegistry meterRegistry,
            JobRepository jobRepository,
            JobOutboxEventRepository jobOutboxEventRepository,
            @Qualifier("jobDispatchQueue") BlockingQueue<QueuedJob> jobDispatchQueue,
            ObjectProvider<JobDispatchCapacityService> jobDispatchCapacityServiceProvider,
            ObjectProvider<JobWorkerService> jobWorkerServiceProvider
    ) {
        this.meterRegistry = meterRegistry;
        for (JobStatus status : JobStatus.values()) {
            Gauge gauge = Gauge.builder("nano.job.status.count", jobRepository, repo -> repo.countByStatus(status))
                    .description("Current number of jobs by status")
                    .tag("status", status.name())
                    .register(meterRegistry);
            statusGauges.put(status, gauge);
        }
        for (OutboxStatus status : OutboxStatus.values()) {
            Gauge.builder("nano.job.outbox.count", jobOutboxEventRepository, repo -> repo.countByStatus(status))
                    .description("Current number of outbox events by status")
                    .tag("status", status.name())
                    .register(meterRegistry);
        }

        Gauge.builder("nano.job.executor.active.count",
                        jobWorkerServiceProvider,
                        provider -> {
                            JobWorkerService service = provider.getIfAvailable();
                            return service == null ? 0 : service.getActiveExecutionCount();
                        })
                .description("Current active nano-job job executions")
                .register(meterRegistry);
        Gauge.builder("nano.job.executor.queue.size", jobDispatchQueue, BlockingQueue::size)
                .description("Current nano-job dispatch queue size")
                .register(meterRegistry);
        Gauge.builder("nano.job.dispatch.available_slots",
                        jobDispatchCapacityServiceProvider,
                        provider -> {
                            JobDispatchCapacityService service = provider.getIfAvailable();
                            return service == null ? 0 : service.availableSubmissionSlots();
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

    public void recordOutboxStaged(JobType type) {
        counter("nano.job.outbox.staged", type).increment();
    }

    public void recordOutboxPublished(JobType type) {
        counter("nano.job.outbox.published", type).increment();
    }

    public void recordOutboxRetried(JobType type) {
        counter("nano.job.outbox.retried", type).increment();
    }

    public void recordOutboxProcessed(JobType type) {
        counter("nano.job.outbox.processed", type).increment();
    }

    public void recordOutboxDiscarded(JobType type) {
        counter("nano.job.outbox.discarded", type).increment();
    }

    private Counter counter(String name, JobType type) {
        return Counter.builder(name)
                .description("nano-job execution metric")
                .tags(Tags.of("type", type.name()))
                .register(meterRegistry);
    }
}
