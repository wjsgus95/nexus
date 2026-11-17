package com.nexus;

import com.nexus.config.ConfigRepository;
import com.nexus.config.target.TargetConfig;
import com.nexus.config.testcase.TestCaseSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigAndDoctorOfflineTest {

    @Test
    void doctorOfflineValidatesAllSixSampleTargets() {
        Path catalogKrProfile = Path.of("configs/catalog/kr/profile.yaml");
        TargetConfig catalogKr = ConfigRepository.loadTarget(catalogKrProfile);
        assertThat(catalogKr.getDiffRules().getIgnoredPaths()).contains("$.request_id", "$.timestamp", "$.data.cacheAgeMs");
        assertThat(ConfigRepository.loadSuites(catalogKrProfile, "smoke")).hasSize(2);
        assertThat(ConfigRepository.loadSuites(catalogKrProfile, "regression")).hasSize(2);

        List<TestCaseSpec> smokeTests = ConfigRepository.loadTestCases(catalogKrProfile, "smoke");
        assertThat(smokeTests).hasSize(2);
        assertThat(smokeTests.get(0).getId()).isEqualTo("catalog-kr-categories-get");
        assertThat(smokeTests.get(1).getId()).isEqualTo("catalog-kr-batch-lookup-post");
        assertThat(smokeTests.get(1).getRequest().rawBodyString()).contains("\"SKU-KR-1001\"");

        List<String> targets = List.of(
                "catalog/kr", "catalog/tw",
                "search/kr", "search/tw",
                "pricing/kr", "pricing/tw"
        );
        for (String target : targets) {
            Path profile = Path.of("configs", target, "profile.yaml");
            assertThat(ConfigRepository.loadSuites(profile, "smoke")).hasSizeGreaterThanOrEqualTo(2);
            assertThat(ConfigRepository.loadSuites(profile, "regression")).hasSizeGreaterThanOrEqualTo(2);

            StringWriter out = new StringWriter();
            CommandLine cmd = new CommandLine(new Nexus());
            cmd.setOut(new PrintWriter(out));
            int exitCode = cmd.execute("doctor", "--offline", "-p", profile.toString());

            assertThat(exitCode).isEqualTo(0);
            assertThat(out.toString()).contains(target.replace('/', '-'));
        }
    }

    @Test
    void doctorOfflineRejectsInvalidYamlSyntax(@TempDir Path tempDir) throws IOException {
        Path targetDir = tempDir.resolve("svc/kr");
        Files.createDirectories(targetDir);
        Path profile = targetDir.resolve("profile.yaml");
        Files.writeString(profile, """
                name: svc-kr
                targetUrl: [unclosed
                """);

        StringWriter out = new StringWriter();
        CommandLine cmd = new CommandLine(new Nexus());
        cmd.setOut(new PrintWriter(out));
        int exitCode = cmd.execute("doctor", "--offline", "-p", profile.toString());

        assertThat(exitCode).isEqualTo(1);
        assertThat(out.toString()).contains("[FAIL]").contains("Failed to parse TargetConfig");
    }

    @Test
    void doctorOfflineRejectsMissingRequiredTargetFields(@TempDir Path tempDir) throws IOException {
        Path targetDir = tempDir.resolve("svc/kr");
        Files.createDirectories(targetDir);
        Path profile = targetDir.resolve("profile.yaml");
        Files.writeString(profile, """
                name: svc-kr
                stableUrl: https://prod.example.com
                """);

        StringWriter out = new StringWriter();
        CommandLine cmd = new CommandLine(new Nexus());
        cmd.setOut(new PrintWriter(out));
        int exitCode = cmd.execute("doctor", "--offline", "-p", profile.toString());

        assertThat(exitCode).isEqualTo(1);
        assertThat(out.toString()).contains("[FAIL]").contains("'targetUrl' must not be blank");
    }

    @Test
    void doctorOfflineRejectsMalformedOrNonObjectPostBody(@TempDir Path tempDir) throws IOException {
        Path smokeDir = tempDir.resolve("svc/kr/smoke");
        Files.createDirectories(smokeDir);
        Path profile = tempDir.resolve("svc/kr/profile.yaml");
        writeValidTarget(profile);
        Files.writeString(smokeDir.resolve("bad_post.yaml"), """
                tests:
                  - id: bad-json-body
                    request:
                      method: POST
                      path: /v1/search
                      body: {
                        "keyword": "노트북"
                        "broken": 1
                      }
                """);

        StringWriter out1 = new StringWriter();
        CommandLine cmd1 = new CommandLine(new Nexus());
        cmd1.setOut(new PrintWriter(out1));
        assertThat(cmd1.execute("doctor", "--offline", "-p", profile.toString())).isEqualTo(1);
        assertThat(out1.toString()).contains("[FAIL]").contains("Failed to parse TestSuiteConfig");

        Files.writeString(smokeDir.resolve("bad_post.yaml"), """
                tests:
                  - id: string-body
                    request:
                      method: POST
                      path: /v1/search
                      body: "plain-string"
                """);

        StringWriter out2 = new StringWriter();
        CommandLine cmd2 = new CommandLine(new Nexus());
        cmd2.setOut(new PrintWriter(out2));
        assertThat(cmd2.execute("doctor", "--offline", "-p", profile.toString())).isEqualTo(1);
        assertThat(out2.toString()).contains("[FAIL]").contains("request.body must be a JSON object or array");
    }

    @Test
    void doctorOfflineRejectsInvalidJsonPathAndDuplicateTestIds(@TempDir Path tempDir) throws IOException {
        Path smokeDir = tempDir.resolve("svc/kr/smoke");
        Files.createDirectories(smokeDir);
        Path profile = tempDir.resolve("svc/kr/profile.yaml");
        Files.writeString(profile, """
                name: svc-kr
                targetUrl: https://target.example.com
                stableUrl: https://prod.example.com
                diffRules:
                  ignoredPaths:
                    - '$.[invalid'
                """);

        StringWriter out1 = new StringWriter();
        CommandLine cmd1 = new CommandLine(new Nexus());
        cmd1.setOut(new PrintWriter(out1));
        assertThat(cmd1.execute("doctor", "--offline", "-p", profile.toString())).isEqualTo(1);
        assertThat(out1.toString()).contains("Invalid JsonPath");

        writeValidTarget(profile);
        Files.writeString(smokeDir.resolve("a.yaml"), """
                tests:
                  - id: dup-id
                    request:
                      method: GET
                      path: /a
                """);
        Files.writeString(smokeDir.resolve("b.yaml"), """
                tests:
                  - id: dup-id
                    request:
                      method: GET
                      path: /b
                """);

        StringWriter out2 = new StringWriter();
        CommandLine cmd2 = new CommandLine(new Nexus());
        cmd2.setOut(new PrintWriter(out2));
        assertThat(cmd2.execute("doctor", "--offline", "-p", profile.toString())).isEqualTo(1);
        assertThat(out2.toString()).contains("Duplicate test case id");
    }

    private static void writeValidTarget(Path path) throws IOException {
        Files.writeString(path, """
                name: svc-kr
                targetUrl: https://target.example.com
                stableUrl: https://prod.example.com
                """);
    }
}
