package com.nexus.report.diff;

import com.nexus.report.LatencySummary;
import com.nexus.report.StageSummary;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DiffReport {
    private final StageSummary summary;
    private final boolean autoNoiseDetection;
    private final LatencySummary targetLatency;
    private final LatencySummary stableLatency;
    @Builder.Default
    private final List<DiffReplayResult> results = List.of();

    public static DiffReport from(boolean autoNoiseDetection, List<DiffReplayResult> results) {
        List<DiffReplayResult> copy = results == null ? List.of() : List.copyOf(results);
        return DiffReport.builder()
                .summary(StageSummary.from(copy))
                .autoNoiseDetection(autoNoiseDetection)
                .targetLatency(LatencySummary.from(copy, r -> r.getTarget().getLatencyMs()))
                .stableLatency(LatencySummary.from(copy, r -> r.getStable().getLatencyMs()))
                .results(copy)
                .build();
    }

    public static DiffReport empty(boolean autoNoiseDetection) {
        return DiffReport.builder()
                .summary(StageSummary.emptyPass())
                .autoNoiseDetection(autoNoiseDetection)
                .targetLatency(LatencySummary.fromLatencies(List.of()))
                .stableLatency(LatencySummary.fromLatencies(List.of()))
                .results(List.of())
                .build();
    }
}
