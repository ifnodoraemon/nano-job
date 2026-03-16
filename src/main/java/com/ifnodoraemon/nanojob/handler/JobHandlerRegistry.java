package com.ifnodoraemon.nanojob.handler;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JobHandlerRegistry {

    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);

    public JobHandlerRegistry(List<JobHandler> handlers) {
        handlers.forEach(handler -> this.handlers.put(handler.getType(), handler));
    }

    public JobHandler get(JobType type) {
        JobHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for type: " + type);
        }
        return handler;
    }
}
