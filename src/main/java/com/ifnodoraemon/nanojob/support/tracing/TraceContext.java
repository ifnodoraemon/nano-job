package com.ifnodoraemon.nanojob.support.tracing;

import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;

public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private TraceContext() {
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String currentOrCreate(String prefix) {
        String existing = getTraceId();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String generated = newTraceId(prefix);
        setTraceId(generated);
        return generated;
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }

    public static Map<String, String> copy() {
        return MDC.getCopyOfContextMap();
    }

    public static void restore(Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(contextMap);
    }

    public static void withTraceId(String traceId, Runnable action) {
        Map<String, String> previous = copy();
        try {
            setTraceId(traceId);
            action.run();
        } finally {
            restore(previous);
        }
    }

    public static String newTraceId(String prefix) {
        String normalizedPrefix = (prefix == null || prefix.isBlank()) ? "trace" : prefix;
        return normalizedPrefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
