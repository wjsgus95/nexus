package com.nexus.report;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class StageSummary {
    private final boolean passed;
    private final int totalRequests;
    private final int passedRequests;
    private final int failedRequests;

    public static StageSummary from(List<? extends StageResult> results) {
        int total = results.size();
        int passedCount = (int) results.stream().filter(StageResult::isPassed).count();
        int failedCount = total - passedCount;
        boolean allPassed = failedCount == 0 && total > 0;
        return new StageSummary(allPassed, total, passedCount, failedCount);
    }

    public static StageSummary emptyPass() {
        return new StageSummary(true, 0, 0, 0);
    }
}
