package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.transport.JobDispatchTransport;
import org.springframework.stereotype.Service;

@Service
public class JobDispatchCapacityService {

    private final JobDispatchTransport jobDispatchTransport;
    private final JobWorkerService jobWorkerService;
    private final NanoJobProperties nanoJobProperties;

    public JobDispatchCapacityService(
            JobDispatchTransport jobDispatchTransport,
            JobWorkerService jobWorkerService,
            NanoJobProperties nanoJobProperties
    ) {
        this.jobDispatchTransport = jobDispatchTransport;
        this.jobWorkerService = jobWorkerService;
        this.nanoJobProperties = nanoJobProperties;
    }

    public int availableSubmissionSlots() {
        int idleWorkers = Math.max(0, nanoJobProperties.getExecution().getPoolSize() - jobWorkerService.getActiveExecutionCount());
        return idleWorkers + jobDispatchTransport.remainingCapacity();
    }
}
