package com.ifnodoraemon.nanojob.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ExecutorConfig {

    @Bean(name = "jobLeaseScheduler")
    public ThreadPoolTaskScheduler jobLeaseScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("nano-job-lease-");
        threadFactory.setDaemon(true);
        scheduler.setThreadFactory(threadFactory);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();
        return scheduler;
    }
}
