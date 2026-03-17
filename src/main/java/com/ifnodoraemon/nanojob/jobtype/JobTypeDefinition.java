package com.ifnodoraemon.nanojob.jobtype;

import com.fasterxml.jackson.databind.JsonNode;
import com.ifnodoraemon.nanojob.domain.enums.JobType;

public interface JobTypeDefinition {

    JobType getType();

    JobTypeDescriptor describe();

    void validatePayload(JsonNode payload);
}
