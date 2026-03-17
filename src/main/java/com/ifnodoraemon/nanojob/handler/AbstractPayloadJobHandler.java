package com.ifnodoraemon.nanojob.handler;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.support.payload.JobPayloadMapper;

public abstract class AbstractPayloadJobHandler<T> implements JobHandler {

    private final JobPayloadMapper jobPayloadMapper;

    protected AbstractPayloadJobHandler(JobPayloadMapper jobPayloadMapper) {
        this.jobPayloadMapper = jobPayloadMapper;
    }

    protected abstract Class<T> payloadType();

    protected abstract void handle(Job job, T payload);

    @Override
    public void handle(Job job) {
        T payload = jobPayloadMapper.readForExecution(job, payloadType());
        handle(job, payload);
    }
}
