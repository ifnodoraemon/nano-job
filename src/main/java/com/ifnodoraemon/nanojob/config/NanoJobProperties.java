package com.ifnodoraemon.nanojob.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nano-job")
public class NanoJobProperties {

    private final Scheduler scheduler = new Scheduler();
    private final Execution execution = new Execution();

    public Scheduler getScheduler() {
        return scheduler;
    }

    public Execution getExecution() {
        return execution;
    }

    public static class Scheduler {

        private Duration pollInterval = Duration.ofSeconds(1);

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }
    }

    public static class Execution {

        private int poolSize = 4;
        private Duration retryDelay = Duration.ofSeconds(30);

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        public Duration getRetryDelay() {
            return retryDelay;
        }

        public void setRetryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay;
        }
    }
}
