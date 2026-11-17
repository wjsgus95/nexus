package com.nexus.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public class AuthHeaders {
    private final String bearerToken;
    private final String consumerToken;

    public Map<String, String> toHeaderMap() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.put("Authorization", "Bearer " + bearerToken);
        }
        if (consumerToken != null && !consumerToken.isBlank()) {
            headers.put("X-Consumer-Token", consumerToken);
        }
        return Map.copyOf(headers);
    }
}
