package com.ifnodoraemon.nanojob.jobtype;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.domain.payload.NoopJobPayload;
import com.ifnodoraemon.nanojob.support.payload.JobPayloadMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NoopJobTypeDefinition extends AbstractPayloadJobTypeDefinition<NoopJobPayload> {

    private static final JobTypeDescriptor DESCRIPTOR = new JobTypeDescriptor(
            JobType.NOOP,
            "Execute an in-process no-op task for pipeline verification.",
            List.of(),
            List.of("note", "sleepMillis")
    );

    public NoopJobTypeDefinition(JobPayloadMapper jobPayloadMapper) {
        super(jobPayloadMapper);
    }

    @Override
    public JobType getType() {
        return JobType.NOOP;
    }

    @Override
    public JobTypeDescriptor describe() {
        return DESCRIPTOR;
    }

    @Override
    protected Class<NoopJobPayload> payloadType() {
        return NoopJobPayload.class;
    }
}
