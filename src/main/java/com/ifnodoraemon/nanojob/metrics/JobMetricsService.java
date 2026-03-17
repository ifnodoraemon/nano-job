package com.ifnodoraemon.nanojob.metrics;

import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JobMetricsService {

    private final MeterRegistry meterRegistry;
    private final Map<JobStatus, Gauge> statusGauges = new EnumMap<>(JobStatus.class);

    public JobMetricsService(MeterRegistry meterRegistry, JobRepository jobRepository) {
        this.meterRegistry = meterRegistry;
        for (JobStatus status : JobStatus.values()) {
            Gauge gauge = Gauge.builder("nano.job.status.count", jobRepository, repo -> repo.countByStatus(status))
                    .description("Current number of jobs by status")
                    .tag("status", status.name())
                    .register(meterRegistry);
            statusGauges.put(status, gauge);
        }
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

    public void recordDispatchClaimed(JobType type) {
        counter("nano.job.dispatch.claimed", type).increment();
    }

    private Counter counter(String name, JobType type) {
        return Counter.builder(name)
                .description("nano-job execution metric")
                .tag("type", type.name())
                .register(meterRegistry);
    }
}
