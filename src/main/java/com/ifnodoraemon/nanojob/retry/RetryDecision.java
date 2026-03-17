package com.ifnodoraemon.nanojob.retry;

import java.time.LocalDateTime;

public record RetryDecision(
        boolean retryable,
        int nextRetryCount,
        LocalDateTime nextRetryAt,
        String reason
) {
}
