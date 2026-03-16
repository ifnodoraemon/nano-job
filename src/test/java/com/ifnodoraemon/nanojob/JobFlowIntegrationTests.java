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
                0
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
    void shouldNotExecuteSameJobTwiceWhenDispatchRunsRepeatedly() {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("sleepMillis", 300),
                LocalDateTime.now().minusSeconds(1),
                0
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
                objectMapper.createObjectNode(),
                LocalDateTime.now().minusSeconds(1),
                1
        ));

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
                    assertThat(job.getRetryCount()).isEqualTo(1);
                    assertThat(job.getNextRetryAt()).isNotNull();
                    assertThat(job.getLastError()).contains("HTTP payload must contain a non-blank url");
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId()))
                            .singleElement()
                            .satisfies(log -> {
                                assertThat(log.getAttemptNo()).isEqualTo(1);
                                assertThat(log.getStatus()).isEqualTo(JobStatus.FAILED);
                                assertThat(log.getFinishedAt()).isNotNull();
                                assertThat(log.getMessage()).contains("HTTP payload must contain a non-blank url");
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
}
