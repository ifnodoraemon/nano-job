package com.ifnodoraemon.nanojob.jobtype;

import com.fasterxml.jackson.databind.JsonNode;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.support.exception.InvalidJobPayloadException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NoopJobTypeDefinition implements JobTypeDefinition {

    private static final JobTypeDescriptor DESCRIPTOR = new JobTypeDescriptor(
            JobType.NOOP,
            "Execute an in-process no-op task for pipeline verification.",
            List.of(),
            List.of("note", "sleepMillis")
    );

    @Override
    public JobType getType() {
        return JobType.NOOP;
    }

    @Override
    public JobTypeDescriptor describe() {
        return DESCRIPTOR;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new InvalidJobPayloadException(JobType.NOOP, "Payload must be a JSON object");
        }

        JsonNode sleepMillis = payload.get("sleepMillis");
        if (sleepMillis != null && (!sleepMillis.canConvertToLong() || sleepMillis.asLong() < 0)) {
            throw new InvalidJobPayloadException(JobType.NOOP, "sleepMillis must be a non-negative number");
        }
    }
}
