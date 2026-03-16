package com.ifnodoraemon.nanojob.handler;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HttpJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpJobHandler.class);

    private final ObjectMapper objectMapper;

    public HttpJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JobType getType() {
        return JobType.HTTP;
    }

    @Override
    public void handle(Job job) {
        try {
            JsonNode payload = objectMapper.readTree(job.getPayload());
            String url = payload.path("url").asText();
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("HTTP payload must contain a non-blank url");
            }
            log.info("HTTP handler placeholder for jobKey={} target={}", job.getJobKey(), url);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid HTTP payload for job " + job.getJobKey(), exception);
        }
    }
}
