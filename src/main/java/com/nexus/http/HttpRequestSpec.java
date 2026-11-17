package com.nexus.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.auth.AuthHeaders;
import com.nexus.config.ConfigRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Shared HTTP request model used by test cases (smoke/regression) and Kafka replay envelopes (diff),
 * executing directly via JDK 21 HttpClient on virtual threads.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HttpRequestSpec {
    private static final Duration MIN_TIMEOUT = Duration.ofMillis(500);
    private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpMethod method = HttpMethod.GET;
    private String path;
    private Map<String, String> query = Map.of();
    private Map<String, String> headers = Map.of();
    private JsonNode body;

    public String rawBodyString() {
        if (body == null || body.isNull()) {
            return null;
        }
        try {
            return ConfigRepository.JSON_MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            return body.toString();
        }
    }

    public HttpCallResult execute(URI baseUrl, AuthHeaders authHeaders, Duration timeout) {
        long startNanos = System.nanoTime();
        HttpMethod effectiveMethod = method != null ? method : HttpMethod.GET;
        Duration effectiveTimeout = (timeout != null && timeout.compareTo(MIN_TIMEOUT) > 0) ? timeout : MIN_TIMEOUT;
        try {
            URI uri = buildUri(baseUrl);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(effectiveTimeout);

            builder.header("Accept", "application/json");
            if (authHeaders != null) {
                authHeaders.toHeaderMap().forEach(builder::header);
            }
            if (headers != null) {
                headers.forEach(builder::header);
            }

            String rawBody = rawBodyString();
            if (rawBody != null) {
                boolean hasContentType = headers != null
                        && headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
                if (!hasContentType) {
                    builder.header("Content-Type", "application/json");
                }
                builder.method(effectiveMethod.name(), HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8));
            } else {
                builder.method(effectiveMethod.name(), HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = SHARED_CLIENT.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            return new HttpCallResult(response, latencyMs, null);
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            return new HttpCallResult(null, latencyMs, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public URI buildUri(URI baseUrl) {
        String base = baseUrl.toString();
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        StringBuilder url = new StringBuilder(normalizedBase).append(normalizedPath);
        if (query != null && !query.isEmpty()) {
            StringJoiner joiner = new StringJoiner("&");
            query.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> joiner.add(
                            encode(entry.getKey()) + "=" + encode(entry.getValue() != null ? entry.getValue() : "")
                    ));
            url.append(normalizedPath.contains("?") ? "&" : "?").append(joiner);
        }
        return URI.create(url.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
