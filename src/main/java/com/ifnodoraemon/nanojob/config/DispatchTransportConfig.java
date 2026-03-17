package com.ifnodoraemon.nanojob.config;

import com.ifnodoraemon.nanojob.transport.JobDispatchTransport;
import com.ifnodoraemon.nanojob.transport.LocalQueueJobDispatchTransport;
import com.ifnodoraemon.nanojob.transport.RedisStreamJobDispatchTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class DispatchTransportConfig {

    @Bean
    @ConditionalOnMissingBean(JobDispatchTransport.class)
    public JobDispatchTransport jobDispatchTransport(
            NanoJobProperties properties,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider
    ) {
        return switch (properties.getTransport().getType()) {
            case LOCAL -> new LocalQueueJobDispatchTransport(properties.getExecution().getQueueCapacity());
            case REDIS -> redisTransport(stringRedisTemplateProvider.getIfAvailable(), properties);
        };
    }

    private JobDispatchTransport redisTransport(
            StringRedisTemplate stringRedisTemplate,
            NanoJobProperties properties
    ) {
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("Redis transport requires a configured StringRedisTemplate");
        }
        return new RedisStreamJobDispatchTransport(stringRedisTemplate, properties);
    }
}
