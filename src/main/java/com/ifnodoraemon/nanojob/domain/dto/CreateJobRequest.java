package com.ifnodoraemon.nanojob.domain.dto;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateJobRequest(
        @NotNull JobType type,
        @NotNull JsonNode payload,
        @NotNull LocalDateTime executeAt,
        @NotNull @Min(0) @Max(10) Integer maxRetry,
        @Size(max = 64) String dedupKey
) {
}
