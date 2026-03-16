package com.ifnodoraemon.nanojob.domain.dto;

import com.ifnodoraemon.nanojob.domain.enums.JobStatus;
import java.time.LocalDateTime;

public record JobLogResponse(
        Integer attemptNo,
        JobStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String message
) {
}
