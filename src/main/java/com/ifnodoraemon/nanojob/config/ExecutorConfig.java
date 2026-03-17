package com.ifnodoraemon.nanojob.config;

import com.ifnodoraemon.nanojob.service.QueuedJob;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import com.ifnodoraemon.nanojob.support.tracing.TraceContext;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ExecutorConfig {

    @Bean(name = "jobTaskExecutor")
    public ThreadPoolTaskExecutor jobTaskExecutor(NanoJobProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        CustomizableThreadFactory threadFactory = new CustomizableThreadFactory("nano-job-");
        threadFactory.setDaemon(true);
        executor.setThreadFactory(threadFactory);
        executor.setCorePoolSize(properties.getExecution().getPoolSize());
        executor.setMaxPoolSize(properties.getExecution().getPoolSize());
        executor.setQueueCapacity(0);
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

    @Bean(name = "jobDispatchQueue")
    public BlockingQueue<QueuedJob> jobDispatchQueue(NanoJobProperties properties) {
        int capacity = properties.getExecution().getQueueCapacity();
        return capacity <= 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(capacity);
    }

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

    private RejectedExecutionHandler rejectedExecutionHandler(NanoJobProperties properties) {
        return switch (properties.getExecution().getRejectionPolicy()) {
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
            case ABORT -> new ThreadPoolExecutor.AbortPolicy();
        };
    }
}
