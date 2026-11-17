package com.nexus;

import com.nexus.config.target.DiffRulesConfig;
import com.nexus.eval.diff.DiffResult;
import com.nexus.eval.diff.FieldDiff;
import com.nexus.eval.diff.JsonDiffEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JsonDiffAndDoctorDiffTest {

    @Test
    void detectsFieldDifferencesAcrossValuesTypesMissingExtraAndArrays() {
        String stable = """
                {
                  "name": "Laptop",
                  "price": 1000,
                  "onlyStable": true,
                  "count": 5,
                  "tags": ["a", "b"]
                }
                """;
        String target = """
                {
                  "name": "Tablet",
                  "price": "1000",
                  "onlyTarget": "extra",
                  "count": 5.0,
                  "tags": ["a", "c"]
                }
                """;

        DiffResult result = JsonDiffEngine.diff(stable, target, new DiffRulesConfig(List.of()));
        assertThat(result.isIdentical()).isFalse();
        assertThat(result.getDiffs())
                .extracting(FieldDiff::getPath, FieldDiff::getStableValue, FieldDiff::getTargetValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("$.name", "Laptop", "Tablet"),
                        org.assertj.core.groups.Tuple.tuple("$.price", "1000", "1000"),
                        org.assertj.core.groups.Tuple.tuple("$.onlyStable", "true", null),
                        org.assertj.core.groups.Tuple.tuple("$.onlyTarget", null, "extra"),
                        org.assertj.core.groups.Tuple.tuple("$.tags[1]", "b", "c")
                );
    }

    @Test
    void ignoresConfiguredJsonPathFields() {
        String stable = """
                {
                  "request_id": "req-111",
                  "timestamp": "2026-11-13T00:00:00Z",
                  "items": [
                    { "id": 1, "name": "A" },
                    { "id": 2, "name": "B" }
                  ]
                }
                """;
        String target = """
                {
                  "request_id": "req-222",
                  "timestamp": "2026-11-13T00:00:05Z",
                  "items": [
                    { "id": 1, "name": "A" },
                    { "id": 2, "name": "B" }
                  ]
                }
                """;

        DiffResult result = JsonDiffEngine.diff(
                stable, target, new DiffRulesConfig(List.of("$.request_id", "$.timestamp")));
        assertThat(result.isIdentical()).isTrue();
    }

    @Test
    void detectsAutoNoisePathsFromDoubleStableCalls() {
        String stable1 = """
                { "dynamic_nonce": "abc-1", "meta": { "server": "pod-a" }, "price": 12000 }
                """;
        String stable2 = """
                { "dynamic_nonce": "abc-2", "meta": { "server": "pod-b" }, "price": 12000 }
                """;
        String target = """
                { "dynamic_nonce": "xyz-9", "meta": { "server": "pod-c" }, "price": 12000 }
                """;

        Set<String> noise = JsonDiffEngine.detectAutoNoisePaths(stable1, stable2);
        assertThat(noise).containsExactlyInAnyOrder("$.dynamic_nonce", "$.meta.server");

        DiffResult diff = JsonDiffEngine.diff(stable1, target, new DiffRulesConfig(List.of()), noise);
        assertThat(diff.isIdentical()).isTrue();
    }

    @Test
    void doctorDiffCliAppliesTargetRulesAndWritesReports(@TempDir Path tempDir) throws IOException {
        Path stableFile = tempDir.resolve("stable.json");
        Path targetFile = tempDir.resolve("target.json");

        Files.writeString(stableFile, """
                {
                  "request_id": "base-1",
                  "timestamp": "2026-11-13T01:00:00Z",
                  "meta": { "traceId": "t1" },
                  "data": { "cacheAgeMs": 12, "title": "Notebook" }
                }
                """);
        Files.writeString(targetFile, """
                {
                  "request_id": "stg-2",
                  "timestamp": "2026-11-13T01:00:09Z",
                  "meta": { "traceId": "t2" },
                  "data": { "cacheAgeMs": 99, "title": "Notebook" }
                }
                """);

        // Uses configs/catalog/kr/profile.yaml which ignores request_id, timestamp, meta.traceId, data.cacheAgeMs
        StringWriter outPass = new StringWriter();
        CommandLine cmdPass = new CommandLine(new Nexus());
        cmdPass.setOut(new PrintWriter(outPass));
        int passExit = cmdPass.execute(
                "doctor", "diff",
                stableFile.toString(), targetFile.toString(),
                "-p", "configs/catalog/kr/profile.yaml"
        );
        assertThat(passExit).isEqualTo(0);
        assertThat(outPass.toString()).contains("PASS: No JSON differences found.");

        // Introduce a real difference
        Files.writeString(targetFile, """
                {
                  "request_id": "stg-2",
                  "timestamp": "2026-11-13T01:00:09Z",
                  "meta": { "traceId": "t2" },
                  "data": { "cacheAgeMs": 99, "title": "Changed Title" }
                }
                """);

        StringWriter outFail = new StringWriter();
        CommandLine cmdFail = new CommandLine(new Nexus());
        cmdFail.setOut(new PrintWriter(outFail));
        int failExit = cmdFail.execute(
                "doctor", "diff",
                stableFile.toString(), targetFile.toString(),
                "-p", "configs/catalog/kr/profile.yaml"
        );
        assertThat(failExit).isEqualTo(1);
        assertThat(outFail.toString()).contains("$.data.title");

        // Now ignore $.data.title via --ignore flag
        StringWriter outIgnored = new StringWriter();
        CommandLine cmdIgnored = new CommandLine(new Nexus());
        cmdIgnored.setOut(new PrintWriter(outIgnored));
        int ignoredExit = cmdIgnored.execute(
                "doctor", "diff",
                stableFile.toString(), targetFile.toString(),
                "-p", "configs/catalog/kr/profile.yaml",
                "--ignore", "$.data.title"
        );
        assertThat(ignoredExit).isEqualTo(0);
    }
}
