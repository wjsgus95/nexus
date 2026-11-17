package com.nexus.report.regression;

import com.nexus.eval.diff.FieldDiff;
import com.nexus.http.HttpMethod;
import com.nexus.report.EndpointCall;
import com.nexus.report.StageResult;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RegressionResult implements StageResult {
    private final String id;
    private final String description;
    private final HttpMethod method;
    private final String path;
    private final boolean passed;
    private final String failureReason;
    private final EndpointCall target;
    private final EndpointCall stable;
    @Builder.Default
    private final List<FieldDiff> diffs = List.of();
}
