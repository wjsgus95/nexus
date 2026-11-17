package com.nexus.http;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.net.http.HttpResponse;

@Getter
@AllArgsConstructor
public class HttpCallResult {
    private final HttpResponse<String> response;
    private final long latencyMs;
    private final String error;

    public int getStatusCode() {
        return response != null ? response.statusCode() : 0;
    }

    public String getBody() {
        return response != null ? response.body() : null;
    }

    public boolean isTransportSuccess() {
        return error == null && getStatusCode() > 0;
    }
}
