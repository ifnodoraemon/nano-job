package com.ifnodoraemon.nanojob.domain.dto;

import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import java.time.LocalDateTime;

public record CancelJobResponse(
        String jobKey,
        JobStatus status,
        LocalDateTime canceledAt
) {
}
