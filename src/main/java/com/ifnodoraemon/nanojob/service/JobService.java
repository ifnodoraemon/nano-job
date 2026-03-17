package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.domain.dto.CancelJobResponse;
import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;
import com.ifnodoraemon.nanojob.domain.dto.JobLogResponse;
import com.ifnodoraemon.nanojob.domain.dto.JobResponse;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.dedup.DeduplicationAction;
import com.ifnodoraemon.nanojob.dedup.DeduplicationPolicy;
import com.ifnodoraemon.nanojob.jobtype.JobTypeDefinitionRegistry;
import com.ifnodoraemon.nanojob.repository.JobExecutionLogRepository;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import com.ifnodoraemon.nanojob.support.exception.DuplicateJobSubmissionException;
import com.ifnodoraemon.nanojob.support.exception.InvalidJobStateException;
import com.ifnodoraemon.nanojob.support.exception.JobNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JobService {

    private final JobRepository jobRepository;
    private final JobExecutionLogRepository jobExecutionLogRepository;
    private final JobTypeDefinitionRegistry jobTypeDefinitionRegistry;
    private final DeduplicationPolicy deduplicationPolicy;
    private final ObjectMapper objectMapper;

    public JobService(
            JobRepository jobRepository,
            JobExecutionLogRepository jobExecutionLogRepository,
            JobTypeDefinitionRegistry jobTypeDefinitionRegistry,
            DeduplicationPolicy deduplicationPolicy,
            ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.jobExecutionLogRepository = jobExecutionLogRepository;
        this.jobTypeDefinitionRegistry = jobTypeDefinitionRegistry;
        this.deduplicationPolicy = deduplicationPolicy;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobResponse createJob(CreateJobRequest request) {
        jobTypeDefinitionRegistry.get(request.type()).validatePayload(request.payload());
        String normalizedDedupKey = normalizeDedupKey(request.dedupKey());
        var dedupDecision = deduplicationPolicy.evaluate(request, normalizedDedupKey);
        if (dedupDecision.action() == DeduplicationAction.RETURN_EXISTING) {
            return toResponse(dedupDecision.existingJob());
        }
        if (dedupDecision.action() == DeduplicationAction.REJECT) {
            throw new DuplicateJobSubmissionException(dedupDecision.reason());
        }

        Job job = new Job();
        job.setJobKey(generateJobKey());
        job.setDedupKey(normalizedDedupKey);
        job.setType(request.type());
        job.setStatus(JobStatus.PENDING);
        job.setPayload(writePayload(request.payload()));
        job.setExecuteAt(request.executeAt());
        job.setMaxRetry(request.maxRetry());
        job.setRetryCount(0);
        return toResponse(jobRepository.save(job));
    }

    public JobResponse getJob(String jobKey) {
        return toResponse(findJob(jobKey));
    }

    public Page<JobResponse> listJobs(JobStatus status, JobType type, Pageable pageable) {
        Specification<Job> specification = (root, query, builder) -> builder.conjunction();
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (type != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("type"), type));
        }
        return jobRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional
    public CancelJobResponse cancelJob(String jobKey) {
        Job job = findJob(jobKey);
        if (job.getStatus() == JobStatus.SUCCESS
                || job.getStatus() == JobStatus.FAILED
                || job.getStatus() == JobStatus.CANCELED
                || job.getStatus() == JobStatus.RUNNING) {
            throw new InvalidJobStateException("Job cannot be canceled in status: " + job.getStatus());
        }

        job.setStatus(JobStatus.CANCELED);
        job.setCanceledAt(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        return new CancelJobResponse(saved.getJobKey(), saved.getStatus(), saved.getCanceledAt());
    }

    public java.util.List<JobLogResponse> getJobLogs(String jobKey) {
        Job job = findJob(jobKey);
        return jobExecutionLogRepository.findByJobIdOrderByAttemptNoAsc(job.getId()).stream()
                .map(log -> new JobLogResponse(
                        log.getAttemptNo(),
                        log.getStatus(),
                        log.getStartedAt(),
                        log.getFinishedAt(),
                        log.getMessage(),
                        log.getTraceId()))
                .toList();
    }

    private Job findJob(String jobKey) {
        return jobRepository.findByJobKey(jobKey).orElseThrow(() -> new JobNotFoundException(jobKey));
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getJobKey(),
                job.getDedupKey(),
                job.getType(),
                job.getStatus(),
                readPayload(job.getPayload()),
                job.getExecuteAt(),
                job.getMaxRetry(),
                job.getRetryCount(),
                job.getNextRetryAt(),
                job.getLastError(),
                job.getCanceledAt(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private String generateJobKey() {
        return "NJ" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeDedupKey(String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return null;
        }
        return dedupKey.trim();
    }

    private String writePayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize payload", exception);
        }
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize payload", exception);
        }
    }
}
