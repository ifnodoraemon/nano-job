package com.ifnodoraemon.nanojob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.service.QueuedJob;
import com.ifnodoraemon.nanojob.transport.DispatchDelivery;
import com.ifnodoraemon.nanojob.transport.RedisStreamJobDispatchTransport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisStreamJobDispatchTransportTests {

    private static final String STREAM_KEY = "nano-job:test-stream";
    private static final String CONSUMER_GROUP = "nano-job-test-group";
    private static final String CONSUMER_NAME = "nano-job-test-consumer";

    private StringRedisTemplate stringRedisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private RedisStreamJobDispatchTransport transport;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(STREAM_KEY, ReadOffset.latest(), CONSUMER_GROUP)).thenReturn("OK");
        transport = new RedisStreamJobDispatchTransport(stringRedisTemplate, properties());
    }

    @Test
    void publishShouldEncodeQueuedJobIntoRedisStreamRecord() {
        QueuedJob queuedJob = new QueuedJob(42L, "trace-42", "token-42", 420L);
        when(streamOperations.add(any(StringRecord.class))).thenReturn(RecordId.of("1-0"));

        boolean published = transport.publish(queuedJob);

        assertThat(published).isTrue();
        ArgumentCaptor<StringRecord> recordCaptor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        StringRecord record = recordCaptor.getValue();
        assertThat(record.getStream()).isEqualTo(STREAM_KEY);
        assertThat(record.getValue()).containsEntry("jobId", "42");
        assertThat(record.getValue()).containsEntry("traceId", "trace-42");
        assertThat(record.getValue()).containsEntry("executionToken", "token-42");
        assertThat(record.getValue()).containsEntry("outboxEventId", "420");
    }

    @Test
    @SuppressWarnings("unchecked")
    void takeShouldDecodeQueuedJobAndAcknowledgeRecord() throws InterruptedException {
        RecordId recordId = RecordId.of("1-0");
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(recordId);
        when(record.getValue()).thenReturn(Map.of(
                "jobId", "7",
                "traceId", "trace-7",
                "executionToken", "token-7",
                "outboxEventId", "70"
        ));
        when(streamOperations.read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class)
        )).thenReturn(List.of(record));

        DispatchDelivery delivery = transport.take();

        assertThat(delivery.queuedJob()).isEqualTo(new QueuedJob(7L, "trace-7", "token-7", 70L));
        delivery.ack();
        verify(streamOperations).acknowledge(STREAM_KEY, CONSUMER_GROUP, recordId);
    }

    private NanoJobProperties properties() {
        NanoJobProperties properties = new NanoJobProperties();
        properties.getTransport().getRedis().setStreamKey(STREAM_KEY);
        properties.getTransport().getRedis().setConsumerGroup(CONSUMER_GROUP);
        properties.getTransport().getRedis().setConsumerName(CONSUMER_NAME);
        properties.getTransport().getRedis().setBlockTimeout(Duration.ofMillis(50));
        return properties;
    }
}
