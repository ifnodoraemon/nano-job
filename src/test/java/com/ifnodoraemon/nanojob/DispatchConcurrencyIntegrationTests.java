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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "nano-job.scheduler.poll-interval=10s",
        "nano-job.scheduler.batch-size=10",
        "nano-job.execution.pool-size=4",
        "nano-job.execution.queue-capacity=10"
})
class DispatchConcurrencyIntegrationTests {

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

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        jobExecutionLogRepository.deleteAll();
        jobRepository.deleteAll();
        executorService = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void shouldOnlyExecuteOnceWhenMultipleDispatchersRaceForTheSameJob() throws Exception {
        var created = jobService.createJob(new CreateJobRequest(
                JobType.NOOP,
                objectMapper.createObjectNode().put("sleepMillis", 250),
                LocalDateTime.now().minusSeconds(1),
                0,
                null
        ));

        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(executorService.submit(() -> {
                ready.countDown();
                start.await(2, TimeUnit.SECONDS);
                jobDispatchService.dispatchDueJobs();
                return null;
            }));
        }

        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(2, TimeUnit.SECONDS);
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    var job = jobRepository.findByJobKey(created.jobKey()).orElseThrow();
                    assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCESS);
                    assertThat(jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId())).hasSize(1);
                });
    }
}
