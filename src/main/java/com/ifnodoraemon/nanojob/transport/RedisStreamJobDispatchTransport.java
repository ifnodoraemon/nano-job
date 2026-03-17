package com.ifnodoraemon.nanojob.transport;

import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.service.QueuedJob;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisStreamJobDispatchTransport implements JobDispatchTransport {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamJobDispatchTransport.class);

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
        return streamOperations().add(
                RedisDispatchMessage.fromQueuedJob(queuedJob).toRecord(streamKey)
        ) != null;
    }

    @Override
    public DispatchDelivery take() throws InterruptedException {
        ensureConsumerGroup();
        while (!Thread.currentThread().isInterrupted()) {
            List<MapRecord<String, Object, Object>> records = streamOperations().read(
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
        Long size = streamOperations().size(streamKey);
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
        streamOperations().acknowledge(streamKey, consumerGroup, record.getId());
    }

    private DispatchDelivery toDelivery(MapRecord<String, Object, Object> record) {
        return new DispatchDelivery(
                RedisDispatchMessage.fromRecord(record).toQueuedJob(),
                () -> acknowledge(record),
                () -> log.debug("Leaving Redis stream record pending for retry recordId={}", record.getId().getValue())
        );
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
                streamOperations().createGroup(streamKey, ReadOffset.latest(), consumerGroup);
            } catch (Exception exception) {
                if (isBusyGroup(exception)) {
                    log.debug("Redis consumer group already exists streamKey={} group={}", streamKey, consumerGroup);
                    groupInitialized.set(true);
                    return;
                }
                bootstrapStream();
                try {
                    streamOperations().createGroup(streamKey, ReadOffset.latest(), consumerGroup);
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
        streamOperations().add(
                org.springframework.data.redis.connection.stream.StreamRecords.string(Map.of("bootstrap", "1"))
                        .withStreamKey(streamKey)
        );
    }

    @SuppressWarnings("unchecked")
    private StreamOperations<String, Object, Object> streamOperations() {
        return (StreamOperations<String, Object, Object>) stringRedisTemplate.opsForStream();
    }

    private boolean isBusyGroup(Exception exception) {
        String message = exception.getMessage();
        return message != null && message.contains("BUSYGROUP");
    }
}
