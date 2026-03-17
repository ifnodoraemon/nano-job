package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import java.util.concurrent.BlockingQueue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class JobDispatchCapacityService {

    private final BlockingQueue<QueuedJob> jobDispatchQueue;
    private final JobWorkerService jobWorkerService;
    private final NanoJobProperties nanoJobProperties;

    public JobDispatchCapacityService(
            @Qualifier("jobDispatchQueue") BlockingQueue<QueuedJob> jobDispatchQueue,
            JobWorkerService jobWorkerService,
            NanoJobProperties nanoJobProperties
    ) {
        this.jobDispatchQueue = jobDispatchQueue;
        this.jobWorkerService = jobWorkerService;
        this.nanoJobProperties = nanoJobProperties;
    }

    public int availableSubmissionSlots() {
        int idleWorkers = Math.max(0, nanoJobProperties.getExecution().getPoolSize() - jobWorkerService.getActiveExecutionCount());
        return idleWorkers + jobDispatchQueue.remainingCapacity();
    }
}
