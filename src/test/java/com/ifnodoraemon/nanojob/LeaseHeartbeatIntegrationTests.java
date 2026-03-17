package com.ifnodoraemon.nanojob;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.repository.JobExecutionLogRepository;
import com.ifnodoraemon.nanojob.repository.JobOutboxEventRepository;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.service.JobDispatchService;
import com.ifnodoraemon.nanojob.service.JobLifecycleService;
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
        "nano-job.execution.lease-duration=150ms",
        "nano-job.execution.lease-heartbeat-interval=50ms",
        "nano-job.execution.retry-delay=100ms",
        "nano-job.scheduler.poll-interval=10s"
})
class LeaseHeartbeatIntegrationTests {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobDispatchService jobDispatchService;

    @Autowired
    private JobLifecycleService jobLifecycleService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobExecutionLogRepository jobExecutionLogRepository;

    @Autowired
    private JobOutboxEventRepository jobOutboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        jobOutboxEventRepository.deleteAll();
        jobExecutionLogRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void shouldRenewLeaseForLongRunningJobAndIgnoreStaleRecoverySnapshot() {
        double renewedBefore = counterValue("nano.job.execution.lease_renewed", JobType.NOOP);
        var created = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("sleepMillis", 450),
                LocalDateTime.now().minusSeconds(1),
                0,
                null
        ));

        jobDispatchService.dispatchDueJobs();

        var runningSnapshot = Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> {
                    var job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    return job.getStatus() == JobStatus.RUNNING ? job : null;
                }, java.util.Objects::nonNull);

        LocalDateTime initialLeaseExpiry = runningSnapshot.getLeaseExpiresAt();

        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    var refreshed = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(refreshed.getLeaseExpiresAt()).isAfter(initialLeaseExpiry);
                    assertThat(counterValue("nano.job.execution.lease_renewed", JobType.NOOP) - renewedBefore)
                            .isGreaterThanOrEqualTo(1.0);
                });

        boolean recovered = jobLifecycleService.recoverTimedOutJob(runningSnapshot);
        assertThat(recovered).isFalse();
        assertThat(jobRepository.findByJobKey(created.jobKey()).orElseThrow().getStatus())
                .isEqualTo(JobStatus.RUNNING);

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCESS);
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId())).hasSize(1);
                });
    }

    private double counterValue(String name, JobType type) {
        var counter = meterRegistry.find(name).tag("type", type.name()).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
