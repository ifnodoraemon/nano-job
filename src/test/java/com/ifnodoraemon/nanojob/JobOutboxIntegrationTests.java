package com.ifnodoraemon.nanojob;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.domain.enums.OutboxStatus;
import com.ifnodoraemon.nanojob.repository.JobExecutionLogRepository;
import com.ifnodoraemon.nanojob.repository.JobOutboxEventRepository;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.service.JobDispatchService;
import com.ifnodoraemon.nanojob.service.JobLifecycleService;
import com.ifnodoraemon.nanojob.service.JobService;
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
        "nano-job.execution.lease-duration=10s",
        "nano-job.outbox.retry-delay=100ms",
        "nano-job.scheduler.capacity-aware-dispatch=false",
        "nano-job.scheduler.poll-interval=10s"
})
class JobOutboxIntegrationTests {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobLifecycleService jobLifecycleService;

    @Autowired
    private JobDispatchService jobDispatchService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobExecutionLogRepository jobExecutionLogRepository;

    @Autowired
    private JobOutboxEventRepository jobOutboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jobOutboxEventRepository.deleteAll();
        jobExecutionLogRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void shouldStageOutboxEventWithExecutionTokenWhenClaimSucceeds() {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "outbox-stage"),
                LocalDateTime.now().minusSeconds(1),
                0,
                null
        ));

        var job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();

        boolean claimed = jobLifecycleService.tryClaimAndStage(job, "trace-outbox-claim");

        assertThat(claimed).isTrue();

        var claimedJob = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
        var outboxEvent = jobOutboxEventRepository.findAll().getFirst();
        assertThat(claimedJob.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(claimedJob.getExecutionToken()).isNotBlank();
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxEvent.getTraceId()).isEqualTo("trace-outbox-claim");
        assertThat(outboxEvent.getExecutionToken()).isEqualTo(claimedJob.getExecutionToken());
        assertThat(outboxEvent.getJobId()).isEqualTo(claimedJob.getId());
    }

    @Test
    void shouldReschedulePendingOutboxPublishWhenDispatchQueueIsBusy() {
        var first = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("sleepMillis", 400),
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
                            .isEqualTo(JobStatus.RUNNING);
                    assertThat(jobOutboxEventRepository.findAll())
                            .anySatisfy(event -> {
                                assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
                                assertThat(event.getPublishAttemptCount()).isGreaterThanOrEqualTo(1);
                            });
                });

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        assertThat(jobRepository.findByJobKey(first.jobKey()).orElseThrow().getStatus())
                                .isEqualTo(JobStatus.SUCCESS));

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(jobRepository.findByJobKey(second.jobKey()).orElseThrow().getStatus())
                            .isEqualTo(JobStatus.SUCCESS);
                    assertThat(jobOutboxEventRepository.findAll())
                            .extracting(event -> event.getStatus())
                            .containsOnly(OutboxStatus.PROCESSED);
                });
    }
}
