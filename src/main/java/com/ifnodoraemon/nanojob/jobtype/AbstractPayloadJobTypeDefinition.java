package com.ifnodoraemon.nanojob.jobtype;

import com.fasterxml.jackson.databind.JsonNode;
import com.ifnodoraemon.nanojob.support.payload.JobPayloadMapper;

public abstract class AbstractPayloadJobTypeDefinition<T> implements JobTypeDefinition {

    private final JobPayloadMapper jobPayloadMapper;

    protected AbstractPayloadJobTypeDefinition(JobPayloadMapper jobPayloadMapper) {
        this.jobPayloadMapper = jobPayloadMapper;
    }

    protected abstract Class<T> payloadType();

    @Override
    public void validatePayload(JsonNode payload) {
        jobPayloadMapper.validateForCreation(getType(), payload, payloadType());
    }
}
