package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.metrics.JobMetricsService;
import com.ifnodoraemon.nanojob.support.tracing.TraceContext;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class JobLeaseHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(JobLeaseHeartbeatService.class);

    private final TaskScheduler jobLeaseScheduler;
    private final JobLifecycleService jobLifecycleService;
    private final NanoJobProperties nanoJobProperties;
    private final JobMetricsService jobMetricsService;

    public JobLeaseHeartbeatService(
            @Qualifier("jobLeaseScheduler") TaskScheduler jobLeaseScheduler,
            JobLifecycleService jobLifecycleService,
            NanoJobProperties nanoJobProperties,
            JobMetricsService jobMetricsService
    ) {
        this.jobLeaseScheduler = jobLeaseScheduler;
        this.jobLifecycleService = jobLifecycleService;
        this.nanoJobProperties = nanoJobProperties;
        this.jobMetricsService = jobMetricsService;
    }

    public LeaseHeartbeatHandle start(Job job) {
        var interval = nanoJobProperties.getExecution().getLeaseHeartbeatInterval();
        if (interval.isZero() || interval.isNegative()) {
            return LeaseHeartbeatHandle.NOOP;
        }

        String traceId = TraceContext.currentOrCreate("lease");
        ScheduledFuture<?> future = jobLeaseScheduler.scheduleAtFixedRate(
                () -> TraceContext.withTraceId(traceId, () -> renewLease(job)),
                Instant.now().plus(interval),
                interval
        );
        return () -> future.cancel(false);
    }

    private void renewLease(Job job) {
        boolean renewed = jobLifecycleService.renewLease(job.getId());
        if (!renewed) {
            log.debug("Skipped lease renewal for jobKey={} because the job is no longer owned by this worker", job.getJobKey());
            return;
        }
        jobMetricsService.recordLeaseRenewed(job.getType());
        log.debug("Renewed lease for jobKey={} traceId={}", job.getJobKey(), TraceContext.getTraceId());
    }

    @FunctionalInterface
    public interface LeaseHeartbeatHandle extends AutoCloseable {

        LeaseHeartbeatHandle NOOP = () -> {
        };

        @Override
        void close();
    }
}
