package com.ifnodoraemon.nanojob.config;

import com.ifnodoraemon.nanojob.support.tracing.TraceContext;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ExecutorConfig {

    @Bean(name = "jobTaskExecutor")
    public ThreadPoolTaskExecutor jobTaskExecutor(NanoJobProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("nano-job-");
        executor.setCorePoolSize(properties.getExecution().getPoolSize());
        executor.setMaxPoolSize(properties.getExecution().getPoolSize());
        executor.setQueueCapacity(properties.getExecution().getQueueCapacity());
        executor.setRejectedExecutionHandler(rejectedExecutionHandler(properties));
        executor.setTaskDecorator(runnable -> {
            Map<String, String> contextMap = TraceContext.copy();
            return () -> {
                Map<String, String> previous = TraceContext.copy();
                try {
                    TraceContext.restore(contextMap);
                    runnable.run();
                } finally {
                    TraceContext.restore(previous);
                }
            };
        });
        executor.initialize();
        return executor;
    }

    @Bean(name = "jobLeaseScheduler")
    public ThreadPoolTaskScheduler jobLeaseScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("nano-job-lease-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();
        return scheduler;
    }

    private RejectedExecutionHandler rejectedExecutionHandler(NanoJobProperties properties) {
        return switch (properties.getExecution().getRejectionPolicy()) {
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
            case ABORT -> new ThreadPoolExecutor.AbortPolicy();
        };
    }
}
