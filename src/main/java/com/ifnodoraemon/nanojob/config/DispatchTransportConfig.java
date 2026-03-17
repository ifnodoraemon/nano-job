package com.ifnodoraemon.nanojob.config;

import com.ifnodoraemon.nanojob.transport.JobDispatchTransport;
import com.ifnodoraemon.nanojob.transport.LocalQueueJobDispatchTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DispatchTransportConfig {

    @Bean
    @ConditionalOnMissingBean(JobDispatchTransport.class)
    public JobDispatchTransport jobDispatchTransport(NanoJobProperties properties) {
        return switch (properties.getTransport().getType()) {
            case LOCAL -> new LocalQueueJobDispatchTransport(properties.getExecution().getQueueCapacity());
            case REDIS -> throw new IllegalStateException(
                    "Redis transport is not implemented yet. Keep nano-job on LOCAL transport for now."
            );
        };
    }
}
