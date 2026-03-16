package com.ifnodoraemon.nanojob.handler;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(NoopJobHandler.class);
    private final ObjectMapper objectMapper;

    public NoopJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JobType getType() {
        return JobType.NOOP;
    }

    @Override
    public void handle(Job job) {
        try {
            JsonNode payload = objectMapper.readTree(job.getPayload());
            long sleepMillis = payload.path("sleepMillis").asLong(0);
            if (sleepMillis > 0) {
                Thread.sleep(sleepMillis);
            }
            log.info("NOOP handler received jobKey={}", job.getJobKey());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NOOP handler interrupted for job " + job.getJobKey(), exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid NOOP payload for job " + job.getJobKey(), exception);
        }
    }
}
