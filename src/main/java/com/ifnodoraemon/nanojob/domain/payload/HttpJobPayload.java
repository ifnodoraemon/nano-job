package com.ifnodoraemon.nanojob.domain.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

public record HttpJobPayload(
        @NotBlank(message = "url must not be blank") String url,
        String method,
        Map<String, String> headers,
        String body,
        @PositiveOrZero(message = "timeoutMillis must be non-negative") Long timeoutMillis
) {
}
