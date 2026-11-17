package com.nexus.runner;

import com.nexus.auth.AuthHeaders;
import com.nexus.auth.AuthProvider;
import com.nexus.config.target.TargetConfig;
import com.nexus.eval.diff.StableEvaluation;
import com.nexus.kafka.KafkaTrafficSource;
import com.nexus.kafka.SampledRequest;
import com.nexus.report.diff.DiffReplayResult;
import com.nexus.report.diff.DiffReport;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.List;

/**
 * Stage 3 (diff): Consumes sampled traffic from Kafka, captures a {@link StableSpec},
 * and evaluates the Target response against it concurrently.
 */
@RequiredArgsConstructor
public class DiffRunner {
    private final AuthProvider authProvider;
    private final KafkaTrafficSource trafficSource;

    public DiffReport run(
            TargetConfig target,
            int concurrencyLimit,
            boolean autoNoiseDetection,
            Duration timeout,
            int retries
    ) {
        AuthHeaders auth = authProvider.authenticate();
        List<SampledRequest> sampledRequests = trafficSource.consumeBatch(target.getKafka());
        if (sampledRequests.isEmpty()) {
            return DiffReport.empty(autoNoiseDetection);
        }
        List<DiffReplayResult> results = StageExecutor.runBoundedWithRetries(
                sampledRequests,
                concurrencyLimit,
                retries,
                sample -> replaySingle(target, sample, auth, autoNoiseDetection, timeout)
        );
        return DiffReport.from(autoNoiseDetection, results);
    }

    private DiffReplayResult replaySingle(
            TargetConfig target,
            SampledRequest sample,
            AuthHeaders auth,
            boolean autoNoiseDetection,
            Duration timeout
    ) {
        StableEvaluation eval = StageExecutor.executeAgainstStable(
                sample.getRequest(), target, auth, timeout, autoNoiseDetection);

        return DiffReplayResult.builder()
                .id(sample.getId())
                .method(sample.getRequest().getMethod())
                .path(sample.getRequest().getPath())
                .passed(eval.isPassed())
                .failureReason(eval.getFailureReason())
                .target(eval.getTargetCall())
                .stable(eval.getStableCall())
                .diffs(eval.getDiffs())
                .autoIgnoredNoisePaths(eval.getAutoIgnoredNoisePaths())
                .build();
    }
}
