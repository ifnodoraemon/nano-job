package com.ifnodoraemon.nanojob.support.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingTraceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        String traceId = (incomingTraceId == null || incomingTraceId.isBlank())
                ? TraceContext.newTraceId("req")
                : incomingTraceId.trim();

        Map<String, String> previous = TraceContext.copy();
        try {
            TraceContext.setTraceId(traceId);
            response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.restore(previous);
        }
    }
}
