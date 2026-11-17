package com.nexus.report.regression;

import com.nexus.report.LatencySummary;
import com.nexus.report.StageSummary;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RegressionReport {
    private final StageSummary summary;
    private final LatencySummary targetLatency;
    private final LatencySummary stableLatency;
    @Builder.Default
    private final List<RegressionResult> results = List.of();

    public static RegressionReport from(List<RegressionResult> results) {
        List<RegressionResult> copy = results == null ? List.of() : List.copyOf(results);
        return RegressionReport.builder()
                .summary(StageSummary.from(copy))
                .targetLatency(LatencySummary.from(copy, r -> r.getTarget().getLatencyMs()))
                .stableLatency(LatencySummary.from(copy, r -> r.getStable().getLatencyMs()))
                .results(copy)
                .build();
    }
}
