package com.ifnodoraemon.nanojob.config;

import com.ifnodoraemon.nanojob.dedup.DeduplicationMode;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nano-job")
public class NanoJobProperties {

    private final Scheduler scheduler = new Scheduler();
    private final Execution execution = new Execution();
    private final Dedup dedup = new Dedup();

    public Scheduler getScheduler() {
        return scheduler;
    }

    public Execution getExecution() {
        return execution;
    }

    public Dedup getDedup() {
        return dedup;
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
        private Duration leaseDuration = Duration.ofSeconds(30);
        private String workerId = "local-worker";

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

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public void setLeaseDuration(Duration leaseDuration) {
            this.leaseDuration = leaseDuration;
        }

        public String getWorkerId() {
            return workerId;
        }

        public void setWorkerId(String workerId) {
            this.workerId = workerId;
        }
    }

    public static class Dedup {

        private DeduplicationMode mode = DeduplicationMode.RETURN_EXISTING;
        private boolean detectDrift = true;
        private Duration window = Duration.ZERO;

        public DeduplicationMode getMode() {
            return mode;
        }

        public void setMode(DeduplicationMode mode) {
            this.mode = mode;
        }

        public boolean isDetectDrift() {
            return detectDrift;
        }

        public void setDetectDrift(boolean detectDrift) {
            this.detectDrift = detectDrift;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}
