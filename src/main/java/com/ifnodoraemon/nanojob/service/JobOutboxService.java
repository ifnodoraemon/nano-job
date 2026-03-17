package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.entity.JobOutboxEvent;
import com.ifnodoraemon.nanojob.domain.enums.OutboxEventType;
import com.ifnodoraemon.nanojob.domain.enums.OutboxStatus;
import com.ifnodoraemon.nanojob.metrics.JobMetricsService;
import com.ifnodoraemon.nanojob.repository.JobOutboxEventRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class JobOutboxService {

    private static final Logger log = LoggerFactory.getLogger(JobOutboxService.class);

    private final JobOutboxEventRepository jobOutboxEventRepository;
    private final NanoJobProperties nanoJobProperties;
    private final BlockingQueue<QueuedJob> jobDispatchQueue;
    private final JobMetricsService jobMetricsService;
    private final TransactionTemplate transactionTemplate;

    public JobOutboxService(
            JobOutboxEventRepository jobOutboxEventRepository,
            NanoJobProperties nanoJobProperties,
            @Qualifier("jobDispatchQueue") BlockingQueue<QueuedJob> jobDispatchQueue,
            JobMetricsService jobMetricsService,
            TransactionTemplate transactionTemplate
    ) {
        this.jobOutboxEventRepository = jobOutboxEventRepository;
        this.nanoJobProperties = nanoJobProperties;
        this.jobDispatchQueue = jobDispatchQueue;
        this.jobMetricsService = jobMetricsService;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public void stageDispatchRequested(Job job, String executionToken, String traceId) {
        JobOutboxEvent event = new JobOutboxEvent();
        event.setEventType(OutboxEventType.DISPATCH_REQUESTED);
        event.setStatus(OutboxStatus.PENDING);
        event.setJobId(job.getId());
        event.setJobType(job.getType());
        event.setExecutionToken(executionToken);
        event.setTraceId(traceId == null ? "outbox-" + job.getJobKey() : traceId);
        event.setAvailableAt(LocalDateTime.now());
        jobOutboxEventRepository.save(event);
        jobMetricsService.recordOutboxStaged(job.getType());
    }

    public void publishPendingDispatches() {
        LocalDateTime now = LocalDateTime.now();
        PageRequest page = PageRequest.of(0, nanoJobProperties.getOutbox().getBatchSize());
        LocalDateTime stalePublishedBefore = now.minus(nanoJobProperties.getOutbox().getRetryDelay());
        var stalePublishedEvents = jobOutboxEventRepository
                .findByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(OutboxStatus.PUBLISHED, stalePublishedBefore, page);
        for (JobOutboxEvent event : stalePublishedEvents) {
            reschedulePublished(event, "Outbox publish timed out for job " + event.getJobId());
        }
        var events = jobOutboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(OutboxStatus.PENDING, now, page);
        for (JobOutboxEvent event : events) {
            if (!markPublished(event.getId())) {
                continue;
            }
            QueuedJob queuedJob = new QueuedJob(
                    event.getJobId(),
                    event.getTraceId(),
                    event.getExecutionToken(),
                    event.getId()
            );
            if (jobDispatchQueue.offer(queuedJob)) {
                jobMetricsService.recordOutboxPublished(event.getJobType());
                continue;
            }
            reschedulePublished(event, "Dispatch queue rejected job " + event.getJobId());
        }

        log.debug("Outbox publisher tick stalePublished={} publishCandidates={}",
                stalePublishedEvents.size(), events.size());
    }

    @Transactional
    public boolean tryStartProcessing(Long eventId) {
        return jobOutboxEventRepository.markProcessing(
                eventId,
                OutboxStatus.PUBLISHED,
                OutboxStatus.PROCESSING,
                LocalDateTime.now()
        ) == 1;
    }

    @Transactional
    public void markProcessed(Long eventId) {
        int updated = jobOutboxEventRepository.markProcessed(
                eventId,
                OutboxStatus.PROCESSING,
                OutboxStatus.PROCESSED,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        if (updated == 1) {
            jobOutboxEventRepository.findById(eventId)
                    .ifPresent(event -> jobMetricsService.recordOutboxProcessed(event.getJobType()));
        }
    }

    @Transactional
    public void markDiscarded(Long eventId, String reason) {
        int updated = jobOutboxEventRepository.markDiscarded(
                eventId,
                EnumSet.of(OutboxStatus.PUBLISHED, OutboxStatus.PROCESSING),
                OutboxStatus.DISCARDED,
                reason,
                LocalDateTime.now()
        );
        if (updated == 1) {
            jobOutboxEventRepository.findById(eventId)
                    .ifPresent(event -> jobMetricsService.recordOutboxDiscarded(event.getJobType()));
        }
    }

    protected boolean markPublished(Long eventId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                jobOutboxEventRepository.markPublished(
                        eventId,
                        OutboxStatus.PENDING,
                        OutboxStatus.PUBLISHED,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                ) == 1
        ));
    }

    protected void reschedulePublished(JobOutboxEvent event, String reason) {
        Integer updated = transactionTemplate.execute(status -> jobOutboxEventRepository.reschedulePending(
                event.getId(),
                OutboxStatus.PUBLISHED,
                OutboxStatus.PENDING,
                event.getPublishAttemptCount() + 1,
                LocalDateTime.now().plus(nanoJobProperties.getOutbox().getRetryDelay()),
                reason,
                LocalDateTime.now()
        ));
        if (updated != null && updated == 1) {
            jobMetricsService.recordOutboxRetried(event.getJobType());
        }
    }
}
