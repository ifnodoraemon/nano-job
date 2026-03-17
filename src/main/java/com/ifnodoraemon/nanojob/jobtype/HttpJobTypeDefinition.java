package com.ifnodoraemon.nanojob.jobtype;

import com.ifnodoraemon.nanojob.domain.payload.HttpJobPayload;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.support.payload.JobPayloadMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HttpJobTypeDefinition extends AbstractPayloadJobTypeDefinition<HttpJobPayload> {

    private static final JobTypeDescriptor DESCRIPTOR = new JobTypeDescriptor(
            JobType.HTTP,
            "Execute an outbound HTTP request placeholder task.",
            List.of("url"),
            List.of("method", "headers", "body", "timeoutMillis")
    );

    public HttpJobTypeDefinition(JobPayloadMapper jobPayloadMapper) {
        super(jobPayloadMapper);
    }

    @Override
    public JobType getType() {
        return JobType.HTTP;
    }

    @Override
    public JobTypeDescriptor describe() {
        return DESCRIPTOR;
    }

    @Override
    protected Class<HttpJobPayload> payloadType() {
        return HttpJobPayload.class;
    }
}
