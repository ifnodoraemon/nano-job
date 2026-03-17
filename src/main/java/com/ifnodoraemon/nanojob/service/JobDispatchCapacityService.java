package com.ifnodoraemon.nanojob.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class JobDispatchCapacityService {

    private final ThreadPoolTaskExecutor jobTaskExecutor;

    public JobDispatchCapacityService(@Qualifier("jobTaskExecutor") ThreadPoolTaskExecutor jobTaskExecutor) {
        this.jobTaskExecutor = jobTaskExecutor;
    }

    public int availableSubmissionSlots() {
        if (jobTaskExecutor.getThreadPoolExecutor() == null) {
            return 0;
        }
        int totalCapacity = jobTaskExecutor.getMaxPoolSize() + jobTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
        int inFlight = jobTaskExecutor.getActiveCount() + jobTaskExecutor.getThreadPoolExecutor().getQueue().size();
        return Math.max(0, totalCapacity - inFlight);
    }
}
