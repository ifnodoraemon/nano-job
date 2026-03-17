package com.ifnodoraemon.nanojob.service;

public record QueuedJob(
        Long jobId,
        String traceId,
        String executionToken,
        Long outboxEventId
) {
}
