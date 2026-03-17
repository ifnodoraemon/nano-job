package com.ifnodoraemon.nanojob.config;

import com.ifnodoraemon.nanojob.dedup.DeduplicationMode;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nano-job")
public class NanoJobProperties {

    private final Scheduler scheduler = new Scheduler();
    private final Execution execution = new Execution();
    private final Dedup dedup = new Dedup();
    private final Http http = new Http();

    public Scheduler getScheduler() {
        return scheduler;
    }

    public Execution getExecution() {
        return execution;
    }

    public Dedup getDedup() {
        return dedup;
    }

    public Http getHttp() {
        return http;
    }

    public static class Scheduler {

        private Duration pollInterval = Duration.ofSeconds(1);
        private int batchSize = 100;

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class Execution {

        private int poolSize = 4;
        private int queueCapacity = 100;
        private ExecutionRejectionPolicy rejectionPolicy = ExecutionRejectionPolicy.ABORT;
        private Duration retryDelay = Duration.ofSeconds(30);
        private Duration leaseDuration = Duration.ofSeconds(30);
        private String workerId = "local-worker";

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public ExecutionRejectionPolicy getRejectionPolicy() {
            return rejectionPolicy;
        }

        public void setRejectionPolicy(ExecutionRejectionPolicy rejectionPolicy) {
            this.rejectionPolicy = rejectionPolicy;
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

    public static class Http {

        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration defaultTimeout = Duration.ofSeconds(2);

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getDefaultTimeout() {
            return defaultTimeout;
        }

        public void setDefaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
        }
    }
}
