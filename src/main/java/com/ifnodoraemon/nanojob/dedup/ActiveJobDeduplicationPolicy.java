package com.ifnodoraemon.nanojob.dedup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.repository.JobRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ActiveJobDeduplicationPolicy implements DeduplicationPolicy {

    private static final Set<JobStatus> ACTIVE_STATUSES = Set.of(
            JobStatus.PENDING,
            JobStatus.RUNNING,
            JobStatus.RETRY_WAIT
    );

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final NanoJobProperties nanoJobProperties;

    public ActiveJobDeduplicationPolicy(
            JobRepository jobRepository,
            ObjectMapper objectMapper,
            NanoJobProperties nanoJobProperties
    ) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.nanoJobProperties = nanoJobProperties;
    }

    @Override
    public DeduplicationDecision evaluate(CreateJobRequest request, String normalizedDedupKey) {
        if (normalizedDedupKey == null) {
            return DeduplicationDecision.createNew();
        }

        return jobRepository.findFirstByDedupKeyAndStatusInOrderByCreatedAtDesc(normalizedDedupKey, ACTIVE_STATUSES)
                .map(existing -> evaluateExisting(request, existing, "active"))
                .orElseGet(() -> evaluateRecentWindow(request, normalizedDedupKey));
    }

    private DeduplicationDecision evaluateRecentWindow(CreateJobRequest request, String normalizedDedupKey) {
        Duration window = nanoJobProperties.getDedup().getWindow();
        if (window == null || window.isZero() || window.isNegative()) {
            return DeduplicationDecision.createNew();
        }

        LocalDateTime cutoff = LocalDateTime.now().minus(window);
        return jobRepository.findFirstByDedupKeyAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(normalizedDedupKey, cutoff)
                .map(existing -> evaluateExisting(request, existing, "recent"))
                .orElseGet(DeduplicationDecision::createNew);
    }

    private DeduplicationDecision evaluateExisting(CreateJobRequest request, Job existing, String scope) {
        if (nanoJobProperties.getDedup().isDetectDrift() && hasSemanticDrift(request, existing)) {
            return DeduplicationDecision.reject(
                    existing,
                    describe(scope) + " job with the same dedupKey exists but type or payload differs"
            );
        }

        if (nanoJobProperties.getDedup().getMode() == DeduplicationMode.REJECT) {
            return DeduplicationDecision.reject(existing, describe(scope) + " job with the same dedupKey already exists");
        }

        return DeduplicationDecision.returnExisting(existing, "Returning existing " + scope + " job for dedupKey");
    }

    private String describe(String scope) {
        return switch (scope) {
            case "active" -> "Active";
            case "recent" -> "Recent";
            default -> "Existing";
        };
    }

    private boolean hasSemanticDrift(CreateJobRequest request, Job existing) {
        if (existing.getType() != request.type()) {
            return true;
        }
        return !readExistingPayload(existing).equals(request.payload());
    }

    private JsonNode readExistingPayload(Job existing) {
        try {
            return objectMapper.readTree(existing.getPayload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize existing payload for job " + existing.getJobKey(),
                    exception
            );
        }
    }
}
