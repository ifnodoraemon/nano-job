package com.ifnodoraemon.nanojob.service;

import com.ifnodoraemon.nanojob.domain.dto.JobTypeDefinitionResponse;
import com.ifnodoraemon.nanojob.jobtype.JobTypeDefinitionRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobTypeService {

    private final JobTypeDefinitionRegistry jobTypeDefinitionRegistry;

    public JobTypeService(JobTypeDefinitionRegistry jobTypeDefinitionRegistry) {
        this.jobTypeDefinitionRegistry = jobTypeDefinitionRegistry;
    }

    public List<JobTypeDefinitionResponse> listJobTypes() {
        return jobTypeDefinitionRegistry.describeAll().stream()
                .map(descriptor -> new JobTypeDefinitionResponse(
                        descriptor.type(),
                        descriptor.description(),
                        descriptor.requiredPayloadFields(),
                        descriptor.optionalPayloadFields()))
                .toList();
    }
}
