package com.ifnodoraemon.nanojob.handler;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.domain.payload.HttpJobPayload;
import com.ifnodoraemon.nanojob.support.payload.JobPayloadMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HttpJobHandler extends AbstractPayloadJobHandler<HttpJobPayload> {

    private static final Logger log = LoggerFactory.getLogger(HttpJobHandler.class);

    public HttpJobHandler(JobPayloadMapper jobPayloadMapper) {
        super(jobPayloadMapper);
    }

    @Override
    public JobType getType() {
        return JobType.HTTP;
    }

    @Override
    protected Class<HttpJobPayload> payloadType() {
        return HttpJobPayload.class;
    }

    @Override
    protected void handle(Job job, HttpJobPayload payload) {
        log.info("HTTP handler placeholder for jobKey={} target={}", job.getJobKey(), payload.url());
    }
}
