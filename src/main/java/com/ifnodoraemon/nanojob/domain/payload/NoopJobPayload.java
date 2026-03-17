package com.ifnodoraemon.nanojob.domain.payload;

import jakarta.validation.constraints.PositiveOrZero;

public record NoopJobPayload(
        String note,
        @PositiveOrZero(message = "sleepMillis must be non-negative") Long sleepMillis
) {
}
