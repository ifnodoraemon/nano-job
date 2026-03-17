package com.ifnodoraemon.nanojob.retry;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class NoopRetryPolicy implements RetryPolicy {

    private final NanoJobProperties properties;

    public NoopRetryPolicy(NanoJobProperties properties) {
        this.properties = properties;
    }

    @Override
    public JobType supports() {
        return JobType.NOOP;
    }

    @Override
    public RetryDecision evaluate(Job job, Exception exception) {
        int nextRetryCount = job.getRetryCount() + 1;
        String reason = buildReason(exception);

        if (nextRetryCount > job.getMaxRetry()) {
            return new RetryDecision(false, nextRetryCount, null, reason);
        }

        // NOOP is our internal sandbox task type, so linear retry is enough.
        LocalDateTime nextRetryAt = LocalDateTime.now()
                .plus(properties.getExecution().getRetryDelay().multipliedBy(nextRetryCount));

        return new RetryDecision(true, nextRetryCount, nextRetryAt, reason);
    }

    private String buildReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
