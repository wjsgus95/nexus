package com.nexus.report.smoke;

import com.nexus.eval.assertion.AssertionResult;
import com.nexus.http.HttpMethod;
import com.nexus.report.EndpointCall;
import com.nexus.report.StageResult;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SmokeResult implements StageResult {
    private final String id;
    private final String description;
    private final HttpMethod method;
    private final String path;
    private final boolean passed;
    private final String failureReason;
    private final EndpointCall target;
    @Builder.Default
    private final List<AssertionResult> assertions = List.of();
}
