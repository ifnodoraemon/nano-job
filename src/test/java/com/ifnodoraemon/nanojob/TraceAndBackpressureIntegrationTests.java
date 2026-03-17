package com.ifnodoraemon.nanojob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.repository.JobExecutionLogRepository;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.service.JobDispatchService;
import com.ifnodoraemon.nanojob.service.JobService;
import com.ifnodoraemon.nanojob.support.tracing.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "nano-job.execution.pool-size=1",
        "nano-job.execution.queue-capacity=0",
        "nano-job.execution.rejection-policy=ABORT",
        "nano-job.execution.retry-delay=100ms",
        "nano-job.scheduler.poll-interval=10s",
        "nano-job.scheduler.capacity-aware-dispatch=false"
})
@AutoConfigureMockMvc
class TraceAndBackpressureIntegrationTests {

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
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        jobExecutionLogRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldExposeTraceIdHeaderForIncomingRequest() throws Exception {
        String traceId = "trace-http-123";
        String requestBody = """
                {
                  "type": "NOOP",
                  "payload": {"note": "trace-header"},
                  "executeAt": "2030-01-01T00:00:00",
                  "maxRetry": 0,
                  "dedupKey": null
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .header(TraceContext.TRACE_ID_HEADER, traceId)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER, traceId));
    }

    @Test
    void shouldPropagateTraceIdIntoAsyncExecutionLog() {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("note", "trace-async"),
                LocalDateTime.now().minusSeconds(1),
                0,
                null
        ));

        TraceContext.withTraceId("trace-async-001", jobDispatchService::dispatchDueJobs);

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCESS);
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId()))
                            .singleElement()
                            .satisfies(log -> assertThat(log.getTraceId()).isEqualTo("trace-async-001"));
                });
    }

    @Test
    void shouldRejectOverflowedExecutionAndRecordMetrics() {
        double rejectedBefore = counterValue("nano.job.execution.rejected", JobType.NOOP);

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
                .atMost(Duration.ofSeconds(4))
                .untilAsserted(() -> {
                    assertThat(jobRepository.findByJobKey(first.jobKey()).orElseThrow().getStatus())
                            .isEqualTo(JobStatus.SUCCESS);
                    assertThat(jobRepository.findByJobKey(second.jobKey()).orElseThrow().getStatus())
                            .isEqualTo(JobStatus.FAILED);
                    assertThat(counterValue("nano.job.execution.rejected", JobType.NOOP) - rejectedBefore)
                            .isEqualTo(1.0);
                });
    }

    private double counterValue(String name, JobType type) {
        var counter = meterRegistry.find(name).tag("type", type.name()).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
