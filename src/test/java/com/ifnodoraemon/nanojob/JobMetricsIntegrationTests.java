package com.ifnodoraemon.nanojob;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.repository.JobExecutionLogRepository;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.service.JobDispatchService;
import com.ifnodoraemon.nanojob.service.JobService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "nano-job.execution.retry-delay=100ms",
        "nano-job.scheduler.poll-interval=10s"
})
class JobMetricsIntegrationTests {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobDispatchService jobDispatchService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobExecutionLogRepository jobExecutionLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        jobExecutionLogRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void shouldRecordSuccessCountersAndClearPendingGauge() {
        double startedBefore = counterValue("nano.job.execution.started", JobType.NOOP);
        double succeededBefore = counterValue("nano.job.execution.succeeded", JobType.NOOP);
        double claimedBefore = counterValue("nano.job.dispatch.claimed", JobType.NOOP);

        jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "metrics-success"),
                LocalDateTime.now().minusSeconds(1),
                0,
                null
        ));

        assertThat(statusGauge(JobStatus.PENDING)).isEqualTo(1.0);

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(counterValue("nano.job.dispatch.claimed", JobType.NOOP) - claimedBefore).isEqualTo(1.0);
                    assertThat(counterValue("nano.job.execution.started", JobType.NOOP) - startedBefore).isEqualTo(1.0);
                    assertThat(counterValue("nano.job.execution.succeeded", JobType.NOOP) - succeededBefore).isEqualTo(1.0);
                    assertThat(statusGauge(JobStatus.PENDING)).isZero();
                    assertThat(statusGauge(JobStatus.SUCCESS)).isEqualTo(1.0);
                });
    }

    @Test
    void shouldRecordRetryAndFinalFailureCounters() throws InterruptedException {
        double failedBefore = counterValue("nano.job.execution.failed", JobType.HTTP);
        double retryBefore = counterValue("nano.job.execution.retry.scheduled", JobType.HTTP);
        double finalFailedBefore = counterValue("nano.job.execution.final_failed", JobType.HTTP);

        var created = jobService.createJob(new CreateJobRequest(
                JobType.HTTP,
                objectMapper.createObjectNode().put("url", "http://retry-metrics.local"),
                LocalDateTime.now().minusSeconds(1),
                1,
                null
        ));

        jobRepository.findByJobKey(created.jobKey()).ifPresent(job -> {
            job.setPayload("{\"url\":\"http://retry-metrics.local\",\"broken\":");
            jobRepository.save(job);
        });

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(counterValue("nano.job.execution.failed", JobType.HTTP) - failedBefore).isEqualTo(1.0);
                    assertThat(counterValue("nano.job.execution.retry.scheduled", JobType.HTTP) - retryBefore).isEqualTo(1.0);
                    assertThat(statusGauge(JobStatus.RETRY_WAIT)).isEqualTo(1.0);
                });

        Thread.sleep(150);
        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(counterValue("nano.job.execution.failed", JobType.HTTP) - failedBefore).isEqualTo(2.0);
                    assertThat(counterValue("nano.job.execution.retry.scheduled", JobType.HTTP) - retryBefore).isEqualTo(1.0);
                    assertThat(counterValue("nano.job.execution.final_failed", JobType.HTTP) - finalFailedBefore)
                            .isEqualTo(1.0);
                    assertThat(statusGauge(JobStatus.FAILED)).isEqualTo(1.0);
                });
    }

    @Test
    void shouldRecordLeaseRecoveryCounter() {
        double recoveredBefore = counterValue("nano.job.execution.lease_recovered", JobType.NOOP);

        Job job = new Job();
        job.setJobKey("NJMETRIC01");
        job.setType(JobType.NOOP);
        job.setStatus(JobStatus.RUNNING);
        job.setPayload(objectMapper.createObjectNode().put("sleepMillis", 1000).toString());
        job.setExecuteAt(LocalDateTime.now().minusSeconds(5));
        job.setMaxRetry(0);
        job.setRetryCount(0);
        job.setLockOwner("worker-metrics");
        job.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
        jobRepository.save(job);

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(counterValue("nano.job.execution.lease_recovered", JobType.NOOP) - recoveredBefore)
                                .isEqualTo(1.0));
    }

    private double counterValue(String name, JobType type) {
        var counter = meterRegistry.find(name).tag("type", type.name()).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double statusGauge(JobStatus status) {
        var gauge = meterRegistry.find("nano.job.status.count").tag("status", status.name()).gauge();
        return gauge == null ? 0.0 : gauge.value();
    }
}
