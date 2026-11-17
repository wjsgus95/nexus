package com.nexus.runner;

import com.nexus.auth.AuthHeaders;
import com.nexus.auth.AuthProvider;
import com.nexus.config.target.TargetConfig;
import com.nexus.config.testcase.TestCaseSpec;
import com.nexus.eval.diff.StableEvaluation;
import com.nexus.report.regression.RegressionReport;
import com.nexus.report.regression.RegressionResult;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.List;

/**
 * Stage 2 (regression): Captures a {@link StableSpec} and evaluates the Target response
 * against it concurrently for each regression test case.
 */
@RequiredArgsConstructor
public class RegressionRunner {
    private final AuthProvider authProvider;

    public RegressionReport run(
            TargetConfig target,
            List<TestCaseSpec> testCases,
            int concurrencyLimit,
            Duration timeout,
            int retries
    ) {
        AuthHeaders auth = authProvider.authenticate();
        List<RegressionResult> results = StageExecutor.runBoundedWithRetries(
                testCases,
                concurrencyLimit,
                retries,
                tc -> executeSingle(target, tc, auth, timeout)
        );
        return RegressionReport.from(results);
    }

    private RegressionResult executeSingle(
            TargetConfig target,
            TestCaseSpec tc,
            AuthHeaders auth,
            Duration timeout
    ) {
        StableEvaluation eval = StageExecutor.executeAgainstStable(
                tc.getRequest(), target, auth, timeout, false);

        return RegressionResult.builder()
                .id(tc.getId())
                .description(tc.getDescription())
                .method(tc.getRequest().getMethod())
                .path(tc.getRequest().getPath())
                .passed(eval.isPassed())
                .failureReason(eval.getFailureReason())
                .target(eval.getTargetCall())
                .stable(eval.getStableCall())
                .diffs(eval.getDiffs())
                .build();
    }
}
