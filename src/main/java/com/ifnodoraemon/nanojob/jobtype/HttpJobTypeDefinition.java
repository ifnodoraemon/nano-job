package com.ifnodoraemon.nanojob.jobtype;

import com.fasterxml.jackson.databind.JsonNode;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.support.exception.InvalidJobPayloadException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HttpJobTypeDefinition implements JobTypeDefinition {

    private static final JobTypeDescriptor DESCRIPTOR = new JobTypeDescriptor(
            JobType.HTTP,
            "Execute an outbound HTTP request placeholder task.",
            List.of("url"),
            List.of("method", "headers", "body", "timeoutMillis")
    );

    @Override
    public JobType getType() {
        return JobType.HTTP;
    }

    @Override
    public JobTypeDescriptor describe() {
        return DESCRIPTOR;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new InvalidJobPayloadException(JobType.HTTP, "Payload must be a JSON object");
        }

        String url = payload.path("url").asText();
        if (url == null || url.isBlank()) {
            throw new InvalidJobPayloadException(JobType.HTTP, "Payload must contain a non-blank url");
        }

        JsonNode timeoutMillis = payload.get("timeoutMillis");
        if (timeoutMillis != null && (!timeoutMillis.canConvertToLong() || timeoutMillis.asLong() < 0)) {
            throw new InvalidJobPayloadException(JobType.HTTP, "timeoutMillis must be a non-negative number");
        }
    }
}
