package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.support.tracing.TraceContext;
import com.ifnodoraemon.nanojob.transport.JobDispatchTransport;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobWorkerService {

    private static final Logger log = LoggerFactory.getLogger(JobWorkerService.class);

    private final JobDispatchTransport jobDispatchTransport;
    private final JobExecutionService jobExecutionService;
    private final NanoJobProperties nanoJobProperties;
    private final AtomicInteger activeExecutions = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public JobWorkerService(
            JobDispatchTransport jobDispatchTransport,
            JobExecutionService jobExecutionService,
            NanoJobProperties nanoJobProperties
    ) {
        this.jobDispatchTransport = jobDispatchTransport;
        this.jobExecutionService = jobExecutionService;
        this.nanoJobProperties = nanoJobProperties;
    }

    @PostConstruct
    void startWorkers() {
        for (int i = 0; i < nanoJobProperties.getExecution().getPoolSize(); i++) {
            Thread.ofPlatform()
                    .daemon(true)
                    .name("nano-job-worker-" + i)
                    .start(this::consumeLoop);
        }
    }

    @PreDestroy
    void stopWorkers() {
        running.set(false);
    }

    public int getActiveExecutionCount() {
        return activeExecutions.get();
    }

    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                QueuedJob queuedJob = jobDispatchTransport.take();
                Map<String, String> previous = TraceContext.copy();
                activeExecutions.incrementAndGet();
                try {
                    TraceContext.setTraceId(queuedJob.traceId());
                    jobExecutionService.process(queuedJob);
                } finally {
                    activeExecutions.decrementAndGet();
                    TraceContext.restore(previous);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.debug("Job worker interrupted, stopping consumer loop");
            } catch (Exception exception) {
                log.error("Unexpected job worker failure", exception);
            }
        }
    }
}
