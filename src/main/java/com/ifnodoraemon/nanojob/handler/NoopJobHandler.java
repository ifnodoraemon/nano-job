package com.ifnodoraemon.nanojob.handler;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.domain.payload.NoopJobPayload;
import com.ifnodoraemon.nanojob.support.payload.JobPayloadMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopJobHandler extends AbstractPayloadJobHandler<NoopJobPayload> {

    private static final Logger log = LoggerFactory.getLogger(NoopJobHandler.class);

    public NoopJobHandler(JobPayloadMapper jobPayloadMapper) {
        super(jobPayloadMapper);
    }

    @Override
    public JobType getType() {
        return JobType.NOOP;
    }

    @Override
    protected Class<NoopJobPayload> payloadType() {
        return NoopJobPayload.class;
    }

    @Override
    protected void handle(Job job, NoopJobPayload payload) {
        try {
            long sleepMillis = payload.sleepMillis() == null ? 0 : payload.sleepMillis();
            if (sleepMillis > 0) {
                Thread.sleep(sleepMillis);
            }
            log.info("NOOP handler received jobKey={}", job.getJobKey());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NOOP handler interrupted for job " + job.getJobKey(), exception);
        }
    }
}
