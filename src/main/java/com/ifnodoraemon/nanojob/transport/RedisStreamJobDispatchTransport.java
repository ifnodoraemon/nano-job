package com.ifnodoraemon.nanojob.transport;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.service.QueuedJob;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisStreamJobDispatchTransport implements JobDispatchTransport {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamJobDispatchTransport.class);

    private static final String FIELD_JOB_ID = "jobId";
    private static final String FIELD_TRACE_ID = "traceId";
    private static final String FIELD_EXECUTION_TOKEN = "executionToken";
    private static final String FIELD_OUTBOX_EVENT_ID = "outboxEventId";

    private final StringRedisTemplate stringRedisTemplate;
    private final String streamKey;
    private final String consumerGroup;
    private final String consumerName;
    private final Duration blockTimeout;
    private final AtomicBoolean groupInitialized = new AtomicBoolean(false);

    public RedisStreamJobDispatchTransport(StringRedisTemplate stringRedisTemplate, NanoJobProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.streamKey = properties.getTransport().getRedis().getStreamKey();
        this.consumerGroup = properties.getTransport().getRedis().getConsumerGroup();
        this.consumerName = properties.getTransport().getRedis().getConsumerName();
        this.blockTimeout = properties.getTransport().getRedis().getBlockTimeout();
    }

    @Override
    public boolean publish(QueuedJob queuedJob) {
        ensureConsumerGroup();
        return stringRedisTemplate.opsForStream().add(toRecord(queuedJob)) != null;
    }

    @Override
    public DispatchDelivery take() throws InterruptedException {
        ensureConsumerGroup();
        while (!Thread.currentThread().isInterrupted()) {
            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                    Consumer.from(consumerGroup, consumerName),
                    StreamReadOptions.empty().count(1).block(blockTimeout),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
            );
            if (records == null || records.isEmpty()) {
                continue;
            }

            MapRecord<String, Object, Object> record = records.getFirst();
            return toDelivery(record);
        }
        throw new InterruptedException("Redis stream consumer interrupted");
    }

    @Override
    public int depth() {
        Long size = stringRedisTemplate.opsForStream().size(streamKey);
        return size == null ? 0 : Math.toIntExact(Math.min(size, Integer.MAX_VALUE));
    }

    @Override
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public DispatchTransportType type() {
        return DispatchTransportType.REDIS;
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        stringRedisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, record.getId());
    }

    private DispatchDelivery toDelivery(MapRecord<String, Object, Object> record) {
        return new DispatchDelivery(
                toQueuedJob(record),
                () -> acknowledge(record),
                () -> log.debug("Leaving Redis stream record pending for retry recordId={}", record.getId().getValue())
        );
    }

    private StringRecord toRecord(QueuedJob queuedJob) {
        return org.springframework.data.redis.connection.stream.StreamRecords.string(Map.of(
                FIELD_JOB_ID, String.valueOf(queuedJob.jobId()),
                FIELD_TRACE_ID, queuedJob.traceId(),
                FIELD_EXECUTION_TOKEN, queuedJob.executionToken(),
                FIELD_OUTBOX_EVENT_ID, String.valueOf(queuedJob.outboxEventId())
        )).withStreamKey(streamKey);
    }

    private QueuedJob toQueuedJob(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        return new QueuedJob(
                Long.valueOf(requiredField(value, FIELD_JOB_ID)),
                requiredField(value, FIELD_TRACE_ID),
                requiredField(value, FIELD_EXECUTION_TOKEN),
                Long.valueOf(requiredField(value, FIELD_OUTBOX_EVENT_ID))
        );
    }

    private String requiredField(Map<Object, Object> value, String field) {
        String resolved = Objects.toString(value.get(field), null);
        if (resolved == null) {
            throw new IllegalStateException("Missing Redis stream field: " + field);
        }
        return resolved;
    }

    private void ensureConsumerGroup() {
        if (groupInitialized.get()) {
            return;
        }
        synchronized (groupInitialized) {
            if (groupInitialized.get()) {
                return;
            }
            try {
                stringRedisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), consumerGroup);
            } catch (Exception exception) {
                if (isBusyGroup(exception)) {
                    log.debug("Redis consumer group already exists streamKey={} group={}", streamKey, consumerGroup);
                    groupInitialized.set(true);
                    return;
                }
                bootstrapStream();
                try {
                    stringRedisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), consumerGroup);
                } catch (Exception nested) {
                    if (!isBusyGroup(nested)) {
                        throw nested;
                    }
                    log.debug("Redis consumer group already exists after bootstrap streamKey={} group={}",
                            streamKey, consumerGroup);
                }
            }
            groupInitialized.set(true);
        }
    }

    private void bootstrapStream() {
        stringRedisTemplate.opsForStream().add(
                org.springframework.data.redis.connection.stream.StreamRecords.string(Map.of("bootstrap", "1"))
                        .withStreamKey(streamKey)
        );
    }

    private boolean isBusyGroup(Exception exception) {
        String message = exception.getMessage();
        return message != null && message.contains("BUSYGROUP");
    }
}
