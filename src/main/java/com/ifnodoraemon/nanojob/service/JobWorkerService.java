package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.support.tracing.TraceContext;
import com.ifnodoraemon.nanojob.transport.DispatchDelivery;
import com.ifnodoraemon.nanojob.transport.JobDispatchTransport;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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
    private final AtomicInteger workerThreadSequence = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService workerExecutor;

    public JobWorkerService(
            JobDispatchTransport jobDispatchTransport,
            JobExecutionService jobExecutionService,
            NanoJobProperties nanoJobProperties
    ) {
        this.jobDispatchTransport = jobDispatchTransport;
        this.jobExecutionService = jobExecutionService;
        this.nanoJobProperties = nanoJobProperties;
        this.workerExecutor = Executors.newFixedThreadPool(
                nanoJobProperties.getExecution().getPoolSize(),
                workerThreadFactory()
        );
    }

    @PostConstruct
    void startWorkers() {
        for (int i = 0; i < nanoJobProperties.getExecution().getPoolSize(); i++) {
            workerExecutor.submit(this::consumeLoop);
        }
    }

    @PreDestroy
    void stopWorkers() {
        running.set(false);
        workerExecutor.shutdownNow();
        try {
            if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Timed out waiting for job workers to stop cleanly");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while shutting down job workers");
        }
    }

    public int getActiveExecutionCount() {
        return activeExecutions.get();
    }

    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                processDelivery(jobDispatchTransport.take());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.debug("Job worker interrupted, stopping consumer loop");
            } catch (Exception exception) {
                log.error("Unexpected job worker failure", exception);
            }
        }
    }

    private void processDelivery(DispatchDelivery delivery) {
        QueuedJob queuedJob = delivery.queuedJob();
        Map<String, String> previous = TraceContext.copy();
        activeExecutions.incrementAndGet();
        try {
            TraceContext.setTraceId(queuedJob.traceId());
            jobExecutionService.process(queuedJob);
            delivery.ack();
        } catch (Exception exception) {
            delivery.retryLater();
            throw exception;
        } finally {
            activeExecutions.decrementAndGet();
            TraceContext.restore(previous);
        }
    }

    private ThreadFactory workerThreadFactory() {
        return runnable -> Thread.ofPlatform()
                .daemon(true)
                .name("nano-job-worker-" + workerThreadSequence.getAndIncrement())
                .unstarted(runnable);
    }
}
