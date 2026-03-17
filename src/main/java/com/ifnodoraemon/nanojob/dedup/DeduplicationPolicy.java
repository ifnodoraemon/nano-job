package com.ifnodoraemon.nanojob.dedup;

import com.ifnodoraemon.nanojob.domain.dto.CreateJobRequest;

public interface DeduplicationPolicy {

    DeduplicationDecision evaluate(CreateJobRequest request, String normalizedDedupKey);
}
