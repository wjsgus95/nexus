package com.nexus.runner;

import com.nexus.auth.AuthHeaders;
import com.nexus.auth.AuthProvider;
import com.nexus.config.target.TargetConfig;
import com.nexus.config.testcase.TestCaseSpec;
import com.nexus.eval.assertion.AssertionEvaluator;
import com.nexus.eval.assertion.AssertionResult;
import com.nexus.http.HttpCallResult;
import com.nexus.report.EndpointCall;
import com.nexus.report.smoke.SmokeReport;
import com.nexus.report.smoke.SmokeResult;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stage 1 (smoke): Executes curated smoke test cases against Target only with bounded concurrency and retries.
 */
@RequiredArgsConstructor
public class SmokeRunner {
    private final AuthProvider authProvider;
    private final AssertionEvaluator assertionEvaluator = new AssertionEvaluator();

    public SmokeReport run(
            TargetConfig target,
            List<TestCaseSpec> testCases,
            int concurrencyLimit,
            Duration timeout,
            int retries
    ) {
        AuthHeaders auth = authProvider.authenticate();
        List<SmokeResult> results = StageExecutor.runBoundedWithRetries(
                testCases,
                concurrencyLimit,
                retries,
                tc -> executeSingle(target, tc, auth, timeout)
        );
        return SmokeReport.from(results);
    }

    private SmokeResult executeSingle(
            TargetConfig target,
            TestCaseSpec tc,
            AuthHeaders auth,
            Duration timeout
    ) {
        HttpCallResult targetRes = tc.getRequest().execute(target.getTargetUrl(), auth, timeout);
        List<AssertionResult> assertions = assertionEvaluator.evaluate(targetRes, tc.getExpect());
        boolean passed = assertions.stream().allMatch(AssertionResult::isPassed);
        String failureReason = passed ? null : assertions.stream()
                .filter(a -> !a.isPassed())
                .map(AssertionResult::getMessage)
                .collect(Collectors.joining("; "));
        return SmokeResult.builder()
                .id(tc.getId())
                .description(tc.getDescription())
                .method(tc.getRequest().getMethod())
                .path(tc.getRequest().getPath())
                .passed(passed)
                .failureReason(failureReason)
                .target(EndpointCall.from(targetRes))
                .assertions(assertions)
                .build();
    }
}
