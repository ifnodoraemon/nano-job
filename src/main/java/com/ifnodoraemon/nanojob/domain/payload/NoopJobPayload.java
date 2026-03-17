package com.ifnodoraemon.nanojob.domain.payload;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.jobtype.JobPayloadSpec;
import jakarta.validation.constraints.PositiveOrZero;

@JobPayloadSpec(
        type = JobType.NOOP,
        description = "Execute an in-process no-op task for pipeline verification."
)
public record NoopJobPayload(
        String note,
        @PositiveOrZero(message = "sleepMillis must be non-negative") Long sleepMillis
) {
}
