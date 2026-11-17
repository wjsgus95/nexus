package com.nexus.report;

import com.nexus.http.HttpCallResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EndpointCall {
    private final int statusCode;
    private final long latencyMs;

    public static EndpointCall from(HttpCallResult result) {
        return new EndpointCall(result.getStatusCode(), result.getLatencyMs());
    }
}
