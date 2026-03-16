package com.ifnodoraemon.nanojob.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    @Bean(name = "jobTaskExecutor")
    public TaskExecutor jobTaskExecutor(NanoJobProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("nano-job-");
        executor.setCorePoolSize(properties.getExecution().getPoolSize());
        executor.setMaxPoolSize(properties.getExecution().getPoolSize());
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
