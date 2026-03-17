package com.ifnodoraemon.nanojob.jobtype;

import com.fasterxml.jackson.databind.JsonNode;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.support.payload.JobPayloadMapper;

public final class PayloadBackedJobTypeDefinition<T> implements JobTypeDefinition {

    private final JobType type;
    private final Class<T> payloadType;
    private final JobTypeDescriptor descriptor;
    private final JobPayloadMapper jobPayloadMapper;

    public PayloadBackedJobTypeDefinition(
            JobType type,
            Class<T> payloadType,
            JobTypeDescriptor descriptor,
            JobPayloadMapper jobPayloadMapper
    ) {
        this.type = type;
        this.payloadType = payloadType;
        this.descriptor = descriptor;
        this.jobPayloadMapper = jobPayloadMapper;
    }

    @Override
    public JobType getType() {
        return type;
    }

    @Override
    public JobTypeDescriptor describe() {
        return descriptor;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        jobPayloadMapper.validateForCreation(type, payload, payloadType);
    }
}
