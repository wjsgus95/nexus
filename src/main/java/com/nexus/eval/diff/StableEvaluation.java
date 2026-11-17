package com.nexus.eval.diff;

import com.nexus.report.EndpointCall;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Set;

@Getter
@AllArgsConstructor
public class StableEvaluation {
    private final EndpointCall targetCall;
    private final EndpointCall stableCall;
    private final List<FieldDiff> diffs;
    private final Set<String> autoIgnoredNoisePaths;
    private final List<String> failures;

    public boolean isPassed() {
        return failures.isEmpty();
    }

    public String getFailureReason() {
        return failures.isEmpty() ? null : String.join("; ", failures);
    }
}
