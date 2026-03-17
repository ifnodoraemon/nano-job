package com.ifnodoraemon.nanojob.service;

public record QueuedJob(
        Long jobId,
        String traceId
) {
    public static QueuedJob poisonPill() {
        return new QueuedJob(-1L, "shutdown");
    }

    public boolean isPoisonPill() {
        return jobId == -1L;
    }
}
