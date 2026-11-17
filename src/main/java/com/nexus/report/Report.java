package com.nexus.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexus.config.ConfigRepository;
import com.nexus.report.diff.DiffReport;
import com.nexus.report.regression.RegressionReport;
import com.nexus.report.smoke.SmokeReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Aggregate root domain entity representing the outcome of one or more stage runs, serializable as report.json.
 * Composes optional stage-specific reports ({@code smoke}, {@code regression}, {@code diff}) with shared value objects.
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Report {
    private final String name;
    private final Instant timestamp;
    private final boolean passed;
    private final SmokeReport smoke;
    private final RegressionReport regression;
    private final DiffReport diff;

    public Path writeJson(Path outputDir) throws IOException {
        return writeJson(outputDir, this);
    }

    public static Path writeJson(Path outputDir, Object payload) throws IOException {
        Files.createDirectories(outputDir);
        Path jsonPath = outputDir.resolve("report.json");
        Files.writeString(jsonPath, ConfigRepository.PRETTY_JSON_MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8);
        return jsonPath;
    }

    @Builder
    private static Report create(
            String name,
            Instant timestamp,
            SmokeReport smoke,
            RegressionReport regression,
            DiffReport diff
    ) {
        boolean hasStage = smoke != null || regression != null || diff != null;
        boolean allPassed = hasStage
                && (smoke == null || smoke.getSummary().isPassed())
                && (regression == null || regression.getSummary().isPassed())
                && (diff == null || diff.getSummary().isPassed());
        return new Report(
                name,
                timestamp != null ? timestamp : Instant.now(),
                allPassed,
                smoke,
                regression,
                diff
        );
    }
}
