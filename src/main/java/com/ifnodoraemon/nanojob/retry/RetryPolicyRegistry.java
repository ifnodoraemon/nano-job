package com.ifnodoraemon.nanojob.retry;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicyRegistry {

    private final Map<JobType, RetryPolicy> policies = new EnumMap<>(JobType.class);

    public RetryPolicyRegistry(List<RetryPolicy> policies) {
        policies.forEach(policy -> this.policies.put(policy.supports(), policy));
    }

    public RetryPolicy get(JobType jobType) {
        RetryPolicy policy = policies.get(jobType);
        if (policy == null) {
            throw new IllegalArgumentException("No retry policy registered for type: " + jobType);
        }
        return policy;
    }
}
