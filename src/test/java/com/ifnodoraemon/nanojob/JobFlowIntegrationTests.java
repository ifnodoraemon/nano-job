package com.ifnodoraemon.nanojob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import com.ifnodoraemon.nanojob.support.exception.DuplicateJobSubmissionException;
import com.ifnodoraemon.nanojob.support.exception.InvalidJobPayloadException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = {
        "nano-job.execution.retry-delay=100ms",
        "nano-job.scheduler.poll-interval=10s",
        "nano-job.dedup.window=5m"
})
class JobFlowIntegrationTests {

    private static HttpServer httpServer;
    private static int httpPort;

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

    @BeforeAll
    static void startHttpServer() throws Exception {
        httpServer = HttpServer.create(new java.net.InetSocketAddress(0), 0);
        httpServer.createContext("/ok", exchange -> writeResponse(exchange, 200, "ok"));
        httpServer.createContext("/server-error", exchange -> writeResponse(exchange, 503, "upstream unavailable"));
        httpServer.createContext("/client-error", exchange -> writeResponse(exchange, 400, "bad request"));
        httpServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            writeResponse(exchange, 200, "slow-ok");
        });
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        httpPort = httpServer.getAddress().getPort();
    }

    @AfterAll
    static void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

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
    void shouldExecuteHttpJobAgainstLiveEndpoint() {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.HTTP,
                objectMapper.createObjectNode()
                        .put("url", httpUrl("/ok"))
                        .put("method", "POST")
                        .put("body", "{\"event\":\"demo\"}"),
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
                            .satisfies(log -> assertThat(log.getStatus()).isEqualTo(JobStatus.SUCCESS));
                });
    }

    @Test
    void shouldRetryHttpJobOnServerErrorStatus() throws InterruptedException {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.HTTP,
                objectMapper.createObjectNode()
                        .put("url", httpUrl("/server-error"))
                        .put("method", "GET"),
                LocalDateTime.now().minusSeconds(1),
                1,
                null
        ));

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
                    assertThat(job.getLastError()).contains("HTTP status 503");
                });

        Thread.sleep(150);
        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
                    assertThat(job.getRetryCount()).isEqualTo(2);
                });
    }

    @Test
    void shouldNotRetryHttpJobOnClientErrorStatus() {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.HTTP,
                objectMapper.createObjectNode()
                        .put("url", httpUrl("/client-error"))
                        .put("method", "GET"),
                LocalDateTime.now().minusSeconds(1),
                3,
                null
        ));

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
                    assertThat(job.getRetryCount()).isEqualTo(1);
                    assertThat(job.getNextRetryAt()).isNull();
                    assertThat(job.getLastError()).contains("HTTP status 400");
                });
    }

    @Test
    void shouldRetryHttpJobOnTimeout() throws InterruptedException {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.HTTP,
                objectMapper.createObjectNode()
                        .put("url", httpUrl("/slow"))
                        .put("method", "GET")
                        .put("timeoutMillis", 50),
                LocalDateTime.now().minusSeconds(1),
                1,
                null
        ));

        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.RETRY_WAIT);
                    assertThat(job.getLastError()).contains("HTTP I/O failure");
                });

        Thread.sleep(150);
        jobDispatchService.dispatchDueJobs();

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    Job job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
                    assertThat(job.getRetryCount()).isEqualTo(2);
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
    void shouldRejectDedupReuseWhenPayloadDrifts() {
        jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "first"),
                LocalDateTime.now().plusSeconds(30),
                0,
                "order-456"
        ));

        assertThatThrownBy(() -> jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "changed"),
                LocalDateTime.now().plusSeconds(30),
                0,
                "order-456"
        ))).isInstanceOf(DuplicateJobSubmissionException.class)
                .hasMessageContaining("type or payload differs");

        assertThat(jobRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldReturnRecentFinishedJobWithinDedupWindow() {
        var first = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "same-request"),
                LocalDateTime.now().plusSeconds(30),
                0,
                "order-789"
        ));

        jobRepository.findByJobKey(first.jobKey()).ifPresent(job -> {
            job.setStatus(JobStatus.SUCCESS);
            jobRepository.save(job);
        });

        var second = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "same-request"),
                LocalDateTime.now().plusSeconds(60),
                0,
                "order-789"
        ));

        assertThat(second.jobKey()).isEqualTo(first.jobKey());
        assertThat(jobRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldCreateNewJobWhenRecentFinishedJobIsOutsideDedupWindow() {
        var first = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "same-request"),
                LocalDateTime.now().plusSeconds(30),
                0,
                "order-999"
        ));

        jobRepository.findByJobKey(first.jobKey()).ifPresent(job -> {
            job.setStatus(JobStatus.SUCCESS);
            ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.now().minusMinutes(10));
            jobRepository.save(job);
        });

        var second = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "same-request"),
                LocalDateTime.now().plusSeconds(60),
                0,
                "order-999"
        ));

        assertThat(second.jobKey()).isNotEqualTo(first.jobKey());
        assertThat(jobRepository.findAll()).hasSize(2);
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

    private static String httpUrl(String path) {
        return "http://127.0.0.1:" + httpPort + path;
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
