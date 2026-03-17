package com.ifnodoraemon.nanojob.domain.dto;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.util.List;

public record JobTypeDefinitionResponse(
        JobType type,
        String description,
        List<String> requiredPayloadFields,
        List<String> optionalPayloadFields
) {
}
