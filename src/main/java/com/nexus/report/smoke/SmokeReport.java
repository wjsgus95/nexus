package com.nexus.report.smoke;

import com.nexus.report.LatencySummary;
import com.nexus.report.StageSummary;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SmokeReport {
    private final StageSummary summary;
    private final LatencySummary targetLatency;
    @Builder.Default
    private final List<SmokeResult> results = List.of();

    public static SmokeReport from(List<SmokeResult> results) {
        List<SmokeResult> copy = results == null ? List.of() : List.copyOf(results);
        return SmokeReport.builder()
                .summary(StageSummary.from(copy))
                .targetLatency(LatencySummary.from(copy, r -> r.getTarget().getLatencyMs()))
                .results(copy)
                .build();
    }
}
