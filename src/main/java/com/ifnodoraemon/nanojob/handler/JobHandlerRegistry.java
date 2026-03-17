package com.ifnodoraemon.nanojob.handler;

import com.ifnodoraemon.nanojob.domain.enums.JobType;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JobHandlerRegistry {

    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);

    public JobHandlerRegistry(List<JobHandler> handlers) {
        handlers.forEach(handler -> {
            JobHandler previous = this.handlers.put(handler.getType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate handler registered for type: " + handler.getType());
            }
        });
    }

    public JobHandler get(JobType type) {
        JobHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for type: " + type);
        }
        return handler;
    }

    public Set<JobType> supportedTypes() {
        EnumSet<JobType> supported = EnumSet.noneOf(JobType.class);
        supported.addAll(handlers.keySet());
        return supported;
    }
}
