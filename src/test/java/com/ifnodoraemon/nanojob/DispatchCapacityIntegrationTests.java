package com.ifnodoraemon.nanojob;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;
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
        "nano-job.execution.pool-size=1",
        "nano-job.execution.queue-capacity=0",
        "nano-job.execution.rejection-policy=ABORT",
        "nano-job.scheduler.poll-interval=10s",
        "nano-job.scheduler.capacity-aware-dispatch=true",
        "nano-job.scheduler.batch-size=10"
})
class DispatchCapacityIntegrationTests {

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
    void shouldThrottleDispatchBeforeExecutorRejectionWhenCapacityIsExhausted() {
        double throttledBefore = counterValue("nano.job.dispatch.throttled", JobType.NOOP);
        double rejectedBefore = counterValue("nano.job.execution.rejected", JobType.NOOP);

        var first = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("sleepMillis", 450),
                LocalDateTime.now().minusSeconds(2),
                0,
                null
        ));
        var second = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("sleepMillis", 10),
                LocalDateTime.now().minusSeconds(1),
                0,
                null
        ));

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(jobRepository.findByJobKey(first.jobKey()).orElseThrow().getStatus())
                            .isIn(JobStatus.RUNNING, JobStatus.SUCCESS);
                    assertThat(jobRepository.findByJobKey(second.jobKey()).orElseThrow().getStatus())
                            .isEqualTo(JobStatus.PENDING);
                    assertThat(counterValue("nano.job.dispatch.throttled", JobType.NOOP) - throttledBefore)
                            .isEqualTo(1.0);
                    assertThat(counterValue("nano.job.execution.rejected", JobType.NOOP) - rejectedBefore)
                            .isEqualTo(0.0);
                });

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(jobRepository.findByJobKey(first.jobKey()).orElseThrow().getStatus())
                                .isEqualTo(JobStatus.SUCCESS));

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(4))
                .untilAsserted(() -> {
                    assertThat(jobRepository.findByJobKey(first.jobKey()).orElseThrow().getStatus())
                            .isEqualTo(JobStatus.SUCCESS);
                    assertThat(jobRepository.findByJobKey(second.jobKey()).orElseThrow().getStatus())
                            .isEqualTo(JobStatus.SUCCESS);
                });
    }

    private double counterValue(String name, JobType type) {
        var counter = meterRegistry.find(name).tag("type", type.name()).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
