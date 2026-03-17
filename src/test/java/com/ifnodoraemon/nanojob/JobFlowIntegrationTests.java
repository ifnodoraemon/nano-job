package com.ifnodoraemon.nanojob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.repository.JobExecutionLogRepository;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.service.JobDispatchService;
import com.ifnodoraemon.nanojob.service.JobService;
import com.ifnodoraemon.nanojob.service.JobTypeService;
import com.ifnodoraemon.nanojob.support.exception.InvalidJobPayloadException;
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
class JobFlowIntegrationTests {

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
    private JobTypeService jobTypeService;

    @BeforeEach
    void setUp() {
        jobExecutionLogRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void shouldExecuteDueNoopJobAndPersistSuccessLog() {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "run-now"),
                LocalDateTime.now().minusSeconds(1),
                0,
                null
        ));

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCESS);
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId()))
                            .singleElement()
                            .satisfies(log -> {
                                assertThat(log.getAttemptNo()).isEqualTo(1);
                                assertThat(log.getStatus()).isEqualTo(JobStatus.SUCCESS);
                                assertThat(log.getFinishedAt()).isNotNull();
                            });
                });
    }

    @Test
    void shouldRejectInvalidPayloadBeforePersistingJob() {
        assertThatThrownBy(() -> jobService.createJob(new CreateJobRequest(
                JobType.HTTP,
                objectMapper.createObjectNode(),
                LocalDateTime.now().plusSeconds(10),
                1,
                null
        ))).isInstanceOf(InvalidJobPayloadException.class)
                .hasMessageContaining("url must not be blank");

        assertThat(jobRepository.findAll()).isEmpty();
    }

    @Test
    void shouldNotExecuteSameJobTwiceWhenDispatchRunsRepeatedly() {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("sleepMillis", 300),
                LocalDateTime.now().minusSeconds(1),
                0,
                null
        ));

        jobDispatchService.dispatchDueJobs();
        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCESS);
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId()))
                            .singleElement()
                            .satisfies(log -> {
                                assertThat(log.getAttemptNo()).isEqualTo(1);
                                assertThat(log.getStatus()).isEqualTo(JobStatus.SUCCESS);
                            });
                });
    }

    @Test
    void shouldRetryFailedHttpJobAndEventuallyMarkItFailed() throws InterruptedException {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.HTTP,
                objectMapper.createObjectNode().put("url", "http://retry.local"),
                LocalDateTime.now().minusSeconds(1),
                1,
                null
        ));

        jobRepository.findByJobKey(created.jobKey()).ifPresent(job -> {
            job.setPayload("{\"url\":\"http://retry.local\",\"broken\":");
            jobRepository.save(job);
        });

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
                    assertThat(job.getRetryCount()).isEqualTo(1);
                    assertThat(job.getNextRetryAt()).isNotNull();
                    assertThat(job.getLastError()).contains("Invalid HTTP payload");
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId()))
                            .singleElement()
                            .satisfies(log -> {
                                assertThat(log.getAttemptNo()).isEqualTo(1);
                                assertThat(log.getStatus()).isEqualTo(JobStatus.FAILED);
                                assertThat(log.getFinishedAt()).isNotNull();
                                assertThat(log.getMessage()).contains("Invalid HTTP payload");
                            });
                });

        Thread.sleep(150);
        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
                    assertThat(job.getRetryCount()).isEqualTo(2);
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId()))
                            .hasSize(2)
                            .extracting(log -> log.getAttemptNo(), log -> log.getStatus())
                            .containsExactly(
                                    org.assertj.core.groups.Tuple.tuple(1, JobStatus.FAILED),
                                    org.assertj.core.groups.Tuple.tuple(2, JobStatus.FAILED)
                            );
                });
    }

    @Test
    void shouldNotRetryNonRetryableExecutionFailure() {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.HTTP,
                objectMapper.createObjectNode().put("url", "http://no-retry.local"),
                LocalDateTime.now().minusSeconds(1),
                3,
                null
        ));

        jobRepository.findByJobKey(created.jobKey()).ifPresent(job -> {
            job.setPayload(objectMapper.createObjectNode().toString());
            jobRepository.save(job);
        });

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
                    assertThat(job.getRetryCount()).isEqualTo(1);
                    assertThat(job.getNextRetryAt()).isNull();
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId()))
                            .singleElement()
                            .satisfies(log -> assertThat(log.getStatus()).isEqualTo(JobStatus.FAILED));
                });
    }

    @Test
    void shouldRetryNoopFailureAccordingToNoopPolicy() throws InterruptedException {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "retry-me"),
                LocalDateTime.now().minusSeconds(1),
                1,
                null
        ));

        jobRepository.findByJobKey(created.jobKey()).ifPresent(job -> {
            job.setPayload("{bad-noop-payload");
            jobRepository.save(job);
        });

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
                    assertThat(job.getRetryCount()).isEqualTo(1);
                    assertThat(job.getLastError()).contains("Invalid NOOP payload");
                });

        Thread.sleep(150);
        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
                    assertThat(job.getRetryCount()).isEqualTo(2);
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId()))
                            .hasSize(2);
                });
    }

    @Test
    void shouldReturnExistingActiveJobWhenDedupKeyMatches() {
        var first = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "same-request"),
                LocalDateTime.now().plusSeconds(30),
                0,
                "order-123"
        ));

        var second = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "same-request"),
                LocalDateTime.now().plusSeconds(60),
                0,
                "order-123"
        ));

        assertThat(second.jobKey()).isEqualTo(first.jobKey());
        assertThat(second.dedupKey()).isEqualTo("order-123");
        assertThat(jobRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldDescribeSupportedJobTypes() {
        assertThat(jobTypeService.listJobTypes())
                .satisfies(definitions -> {
                    assertThat(definitions)
                            .extracting(definition -> definition.type().name())
                            .containsExactly("HTTP", "NOOP");
                    assertThat(definitions.getFirst().requiredPayloadFields()).containsExactly("url");
                    assertThat(definitions.getFirst().optionalPayloadFields())
                            .containsExactly("method", "headers", "body", "timeoutMillis");
                });
    }

    @Test
    void shouldRecoverTimedOutRunningJobAndPreserveFailureHistory() {
        Job job = new Job();
        job.setJobKey("NJTIMEOUT1");
        job.setType(JobType.NOOP);
        job.setStatus(JobStatus.RUNNING);
        job.setPayload(objectMapper.createObjectNode().put("sleepMillis", 1000).toString());
        job.setExecuteAt(LocalDateTime.now().minusSeconds(5));
        job.setMaxRetry(1);
        job.setRetryCount(0);
        job.setLockOwner("worker-a");
        job.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
        Job savedJob = jobRepository.save(job);

        var runningLog = new com.ifnodoraemon.nanojob.domain.entity.JobExecutionLog();
        runningLog.setJobId(savedJob.getId());
        runningLog.setAttemptNo(1);
        runningLog.setStatus(JobStatus.RUNNING);
        jobExecutionLogRepository.save(runningLog);

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job recoveredJob = jobRepository.findByJobKey(savedJob.getJobKey()).orElseThrow();
                    assertThat(recoveredJob.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
                    assertThat(recoveredJob.getRetryCount()).isEqualTo(1);
                    assertThat(recoveredJob.getLockOwner()).isNull();
                    assertThat(recoveredJob.getLeaseExpiresAt()).isNull();
                    assertThat(recoveredJob.getLastError()).contains("Execution lease expired for owner: worker-a");
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(recoveredJob.getId()))
                            .singleElement()
                            .satisfies(log -> {
                                assertThat(log.getAttemptNo()).isEqualTo(1);
                                assertThat(log.getStatus()).isEqualTo(JobStatus.FAILED);
                                assertThat(log.getFinishedAt()).isNotNull();
                                assertThat(log.getMessage()).contains("Execution lease expired for owner: worker-a");
                            });
                });
    }
}
