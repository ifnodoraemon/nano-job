package com.ifnodoraemon.nanojob.jobtype;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.util.List;

public record JobTypeDescriptor(
        JobType type,
        String description,
        List<String> requiredPayloadFields,
        List<String> optionalPayloadFields
) {
}
