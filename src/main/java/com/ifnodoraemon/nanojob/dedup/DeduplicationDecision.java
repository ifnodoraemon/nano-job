package com.ifnodoraemon.nanojob.dedup;

import com.ifnodoraemon.nanojob.domain.entity.Job;

public record DeduplicationDecision(
        DeduplicationAction action,
        Job existingJob,
        String reason
) {

    public static DeduplicationDecision createNew() {
        return new DeduplicationDecision(DeduplicationAction.CREATE_NEW, null, null);
    }

    public static DeduplicationDecision returnExisting(Job existingJob, String reason) {
        return new DeduplicationDecision(DeduplicationAction.RETURN_EXISTING, existingJob, reason);
    }

    public static DeduplicationDecision reject(Job existingJob, String reason) {
        return new DeduplicationDecision(DeduplicationAction.REJECT, existingJob, reason);
    }
}
