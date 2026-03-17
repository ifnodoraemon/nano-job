package com.ifnodoraemon.nanojob.config;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.handler.JobHandlerRegistry;
import com.ifnodoraemon.nanojob.retry.RetryPolicyRegistry;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JobTypeTopologyValidator {

    public JobTypeTopologyValidator(
            JobHandlerRegistry jobHandlerRegistry,
            RetryPolicyRegistry retryPolicyRegistry
    ) {
        Set<JobType> expected = EnumSet.allOf(JobType.class);
        assertCoverage("handler", expected, jobHandlerRegistry.supportedTypes());
        assertCoverage("retry policy", expected, retryPolicyRegistry.supportedTypes());
    }

    private void assertCoverage(String componentName, Set<JobType> expected, Set<JobType> actual) {
        if (!actual.equals(expected)) {
            Set<JobType> missing = EnumSet.noneOf(JobType.class);
            missing.addAll(expected);
            missing.removeAll(actual);
            Set<JobType> extra = EnumSet.noneOf(JobType.class);
            extra.addAll(actual);
            extra.removeAll(expected);
            throw new IllegalStateException(
                    "Job type " + componentName + " coverage mismatch. Missing=" + missing + ", extra=" + extra
            );
        }
    }
}
