package com.ifnodoraemon.nanojob.retry;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class HttpRetryPolicy implements RetryPolicy {

    private final NanoJobProperties properties;

    public HttpRetryPolicy(NanoJobProperties properties) {
        this.properties = properties;
    }

    @Override
    public JobType supports() {
        return JobType.HTTP;
    }

    @Override
    public RetryDecision evaluate(Job job, Exception exception) {
        int nextRetryCount = job.getRetryCount() + 1;
        String reason = buildReason(exception);

        if (exception instanceof IllegalArgumentException) {
            return new RetryDecision(false, nextRetryCount, null, reason);
        }

        if (nextRetryCount > job.getMaxRetry()) {
            return new RetryDecision(false, nextRetryCount, null, reason);
        }

        // HTTP tasks back off more aggressively because they often depend on
        // external systems that may need time to recover.
        long multiplier = 1L << Math.max(0, nextRetryCount - 1);
        LocalDateTime nextRetryAt = LocalDateTime.now()
                .plus(properties.getExecution().getRetryDelay().multipliedBy(multiplier));

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
