package com.ifnodoraemon.nanojob.domain.dto;

import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record JobResponse(
        String jobKey,
        String dedupKey,
        JobType type,
        JobStatus status,
        JsonNode payload,
        LocalDateTime executeAt,
        Integer maxRetry,
        Integer retryCount,
        LocalDateTime nextRetryAt,
        String lastError,
        LocalDateTime canceledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
