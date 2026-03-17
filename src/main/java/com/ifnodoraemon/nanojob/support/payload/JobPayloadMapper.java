package com.ifnodoraemon.nanojob.support.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.support.exception.InvalidJobPayloadException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class JobPayloadMapper {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public JobPayloadMapper(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public <T> void validateForCreation(JobType jobType, JsonNode payload, Class<T> payloadClass) {
        T typedPayload = deserializeTree(jobType, payload, payloadClass);
        validate(jobType, typedPayload, message -> new InvalidJobPayloadException(jobType, message));
    }

    public <T> T readForExecution(Job job, Class<T> payloadClass) {
        T typedPayload;
        try {
            typedPayload = objectMapper.readValue(job.getPayload(), payloadClass);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid " + job.getType() + " payload for job " + job.getJobKey(), exception);
        }
        validate(job.getType(), typedPayload,
                message -> new IllegalArgumentException(
                        "Invalid " + job.getType() + " payload for job " + job.getJobKey() + ": " + message));
        return typedPayload;
    }

    private <T> T deserializeTree(JobType jobType, JsonNode payload, Class<T> payloadClass) {
        try {
            return objectMapper.treeToValue(payload, payloadClass);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidJobPayloadException(jobType,
                    "Payload cannot be deserialized into " + payloadClass.getSimpleName(), exception);
        }
    }

    private <T, E extends RuntimeException> void validate(
            JobType jobType,
            T payload,
            Function<String, E> exceptionFactory
    ) {
        if (payload == null) {
            throw exceptionFactory.apply("Payload must not be null");
        }

        Set<ConstraintViolation<T>> violations = validator.validate(payload);
        if (violations.isEmpty()) {
            return;
        }

        String detail = violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> violation.getMessage())
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("Payload validation failed");
        throw exceptionFactory.apply(detail);
    }
}
