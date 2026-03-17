package com.ifnodoraemon.nanojob.retry;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicyRegistry {

    private final Map<JobType, RetryPolicy> policies = new EnumMap<>(JobType.class);

    public RetryPolicyRegistry(List<RetryPolicy> policies) {
        policies.forEach(policy -> {
            RetryPolicy previous = this.policies.put(policy.supports(), policy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate retry policy registered for type: " + policy.supports());
            }
        });
    }

    public RetryPolicy get(JobType jobType) {
        RetryPolicy policy = policies.get(jobType);
        if (policy == null) {
            throw new IllegalArgumentException("No retry policy registered for type: " + jobType);
        }
        return policy;
    }

    public Set<JobType> supportedTypes() {
        EnumSet<JobType> supported = EnumSet.noneOf(JobType.class);
        supported.addAll(policies.keySet());
        return supported;
    }
}
