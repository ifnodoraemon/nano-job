package com.ifnodoraemon.nanojob.domain.payload;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.jobtype.JobPayloadSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

@JobPayloadSpec(
        type = JobType.HTTP,
        description = "Execute an outbound HTTP request placeholder task."
)
public record HttpJobPayload(
        @NotBlank(message = "url must not be blank") String url,
        String method,
        Map<String, String> headers,
        String body,
        @PositiveOrZero(message = "timeoutMillis must be non-negative") Long timeoutMillis
) {
}
