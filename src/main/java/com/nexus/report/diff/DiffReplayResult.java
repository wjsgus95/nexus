package com.nexus.report.diff;

import com.nexus.eval.diff.FieldDiff;
import com.nexus.http.HttpMethod;
import com.nexus.report.EndpointCall;
import com.nexus.report.StageResult;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

@Getter
@Builder
public class DiffReplayResult implements StageResult {
    private final String id;
    private final HttpMethod method;
    private final String path;
    private final boolean passed;
    private final String failureReason;
    private final EndpointCall target;
    private final EndpointCall stable;
    @Builder.Default
    private final List<FieldDiff> diffs = List.of();
    @Builder.Default
    private final Set<String> autoIgnoredNoisePaths = Set.of();
}
