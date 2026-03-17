package com.ifnodoraemon.nanojob.handler;

import com.ifnodoraemon.nanojob.domain.entity.Job;
import com.ifnodoraemon.nanojob.domain.enums.JobType;
import com.ifnodoraemon.nanojob.domain.payload.HttpJobPayload;
import com.ifnodoraemon.nanojob.config.NanoJobProperties;
import com.ifnodoraemon.nanojob.support.exception.NonRetryableHttpJobException;
import com.ifnodoraemon.nanojob.support.exception.RetryableHttpJobException;
import com.ifnodoraemon.nanojob.support.payload.JobPayloadMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HttpJobHandler extends AbstractPayloadJobHandler<HttpJobPayload> {

    private static final Logger log = LoggerFactory.getLogger(HttpJobHandler.class);
    private static final int MAX_BODY_LOG_LENGTH = 256;

    private final HttpClient httpClient;
    private final NanoJobProperties nanoJobProperties;

    public HttpJobHandler(
            JobPayloadMapper jobPayloadMapper,
            HttpClient jobHttpClient,
            NanoJobProperties nanoJobProperties
    ) {
        super(jobPayloadMapper);
        this.httpClient = jobHttpClient;
        this.nanoJobProperties = nanoJobProperties;
    }

    @Override
    public JobType getType() {
        return JobType.HTTP;
    }

    @Override
    protected Class<HttpJobPayload> payloadType() {
        return HttpJobPayload.class;
    }

    @Override
    protected void handle(Job job, HttpJobPayload payload) {
        HttpRequest request = buildRequest(job, payload);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            handleResponse(job, payload, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetryableHttpJobException("HTTP request interrupted for job " + job.getJobKey(), exception);
        } catch (IOException exception) {
            throw new RetryableHttpJobException("HTTP I/O failure for job " + job.getJobKey(), exception);
        }
    }

    private HttpRequest buildRequest(Job job, HttpJobPayload payload) {
        String method = normalizeMethod(payload.method());
        HttpRequest.BodyPublisher bodyPublisher = buildBodyPublisher(method, payload.body());
        Duration timeout = payload.timeoutMillis() == null
                ? nanoJobProperties.getHttp().getDefaultTimeout()
                : Duration.ofMillis(payload.timeoutMillis());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(toUri(job, payload.url()))
                .timeout(timeout)
                .method(method, bodyPublisher);

        applyHeaders(builder, payload.headers());
        return builder.build();
    }

    private URI toUri(Job job, String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new NonRetryableHttpJobException("HTTP url must be absolute for job " + job.getJobKey());
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new NonRetryableHttpJobException("HTTP url is invalid for job " + job.getJobKey(), exception);
        }
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "GET";
        }

        String normalized = method.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GET", "POST", "PUT", "PATCH", "DELETE" -> normalized;
            default -> throw new NonRetryableHttpJobException("Unsupported HTTP method: " + normalized);
        };
    }

    private HttpRequest.BodyPublisher buildBodyPublisher(String method, String body) {
        if ("GET".equals(method) || "DELETE".equals(method)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
    }

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null) {
                builder.header(name, value);
            }
        });
    }

    private void handleResponse(Job job, HttpJobPayload payload, HttpResponse<String> response) {
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            log.info("HTTP job succeeded jobKey={} method={} url={} status={}",
                    job.getJobKey(), normalizeMethod(payload.method()), payload.url(), statusCode);
            return;
        }

        String responseSummary = summarizeBody(response.body());
        String message = "HTTP status " + statusCode + " for job " + job.getJobKey()
                + (responseSummary.isBlank() ? "" : " body=" + responseSummary);

        if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
            throw new RetryableHttpJobException(message);
        }

        throw new NonRetryableHttpJobException(message);
    }

    private String summarizeBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        if (body.length() <= MAX_BODY_LOG_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LOG_LENGTH) + "...";
    }
}
