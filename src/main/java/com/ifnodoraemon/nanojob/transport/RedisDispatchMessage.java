package com.ifnodoraemon.nanojob.transport;

import com.ifnodoraemon.nanojob.service.QueuedJob;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;

record RedisDispatchMessage(
        Long jobId,
        String traceId,
        String executionToken,
        Long outboxEventId
) {

    private static final String FIELD_JOB_ID = "jobId";
    private static final String FIELD_TRACE_ID = "traceId";
    private static final String FIELD_EXECUTION_TOKEN = "executionToken";
    private static final String FIELD_OUTBOX_EVENT_ID = "outboxEventId";

    static RedisDispatchMessage fromQueuedJob(QueuedJob queuedJob) {
        return new RedisDispatchMessage(
                queuedJob.jobId(),
                queuedJob.traceId(),
                queuedJob.executionToken(),
                queuedJob.outboxEventId()
        );
    }

    static RedisDispatchMessage fromRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        return new RedisDispatchMessage(
                Long.valueOf(requiredField(value, FIELD_JOB_ID)),
                requiredField(value, FIELD_TRACE_ID),
                requiredField(value, FIELD_EXECUTION_TOKEN),
                Long.valueOf(requiredField(value, FIELD_OUTBOX_EVENT_ID))
        );
    }

    QueuedJob toQueuedJob() {
        return new QueuedJob(jobId, traceId, executionToken, outboxEventId);
    }

    StringRecord toRecord(String streamKey) {
        return StreamRecords.string(Map.of(
                FIELD_JOB_ID, String.valueOf(jobId),
                FIELD_TRACE_ID, traceId,
                FIELD_EXECUTION_TOKEN, executionToken,
                FIELD_OUTBOX_EVENT_ID, String.valueOf(outboxEventId)
        )).withStreamKey(streamKey);
    }

    private static String requiredField(Map<Object, Object> value, String field) {
        String resolved = Objects.toString(value.get(field), null);
        if (resolved == null) {
            throw new IllegalStateException("Missing Redis stream field: " + field);
        }
        return resolved;
    }
}
