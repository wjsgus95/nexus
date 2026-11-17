package com.nexus.eval.diff;

import com.nexus.auth.AuthHeaders;
import com.nexus.config.target.DiffRulesConfig;
import com.nexus.http.HttpCallResult;
import com.nexus.http.HttpRequestSpec;
import com.nexus.report.EndpointCall;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Dynamic reference specification captured from Stable (single call, or double call with auto-noise removal)
 * against which a Target HTTP response can be evaluated.
 */
@Getter
@AllArgsConstructor
public class StableSpec {
    private final HttpCallResult response;
    private final Set<String> autoNoisePaths;

    public static StableSpec capture(
            HttpRequestSpec request,
            URI stableUrl,
            AuthHeaders auth,
            Duration timeout,
            boolean autoNoiseDetection
    ) {
        HttpCallResult primary = request.execute(stableUrl, auth, timeout);
        Set<String> noisePaths = Set.of();
        if (autoNoiseDetection && primary.isTransportSuccess()) {
            HttpCallResult verification = request.execute(stableUrl, auth, timeout);
            if (verification.isTransportSuccess()) {
                noisePaths = Set.copyOf(JsonDiffEngine.detectAutoNoisePaths(primary.getBody(), verification.getBody()));
            }
        }
        return new StableSpec(primary, noisePaths);
    }

    public StableEvaluation evaluate(HttpCallResult targetRes, DiffRulesConfig diffRules) {
        List<String> failures = new ArrayList<>();
        if (!response.isTransportSuccess()) {
            failures.add("Stable transport error: " + response.getError());
        }
        if (!targetRes.isTransportSuccess()) {
            failures.add("Target transport error: " + targetRes.getError());
        }
        if (response.isTransportSuccess() && targetRes.isTransportSuccess()
                && response.getStatusCode() != targetRes.getStatusCode()) {
            failures.add("Status mismatch: stable=" + response.getStatusCode() + ", target=" + targetRes.getStatusCode());
        }

        List<FieldDiff> diffs = List.of();
        Set<String> ignoredNoise = autoNoisePaths;
        if (response.isTransportSuccess() && targetRes.isTransportSuccess()) {
            DiffResult diffResult = JsonDiffEngine.diff(response.getBody(), targetRes.getBody(), diffRules, autoNoisePaths);
            diffs = diffResult.getDiffs();
            ignoredNoise = diffResult.getAutoIgnoredNoisePaths();
            if (!diffResult.isIdentical()) {
                failures.add("Response JSON diffed (" + diffs.size() + " difference(s))");
            }
        }

        return new StableEvaluation(
                EndpointCall.from(targetRes),
                EndpointCall.from(response),
                diffs,
                ignoredNoise,
                List.copyOf(failures)
        );
    }
}
