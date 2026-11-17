package com.nexus.kafka;

import com.nexus.http.HttpRequestSpec;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SampledRequest {
    private final String id;
    private final HttpRequestSpec request;
}
