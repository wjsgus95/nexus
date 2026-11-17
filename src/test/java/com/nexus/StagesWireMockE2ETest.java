package com.nexus;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.nexus.config.ConfigRepository;
import com.nexus.config.RunnerEnvConfig;
import com.nexus.http.HttpMethod;
import com.nexus.http.HttpRequestSpec;
import com.nexus.kafka.KafkaTrafficSource;
import com.nexus.kafka.SampledRequest;
import com.nexus.runner.doctor.Doctor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class StagesWireMockE2ETest {
    private WireMockServer stableServer;
    private WireMockServer targetServer;

    @BeforeEach
    void setUp() {
        stableServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        targetServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        stableServer.start();
        targetServer.start();
    }

    @AfterEach
    void tearDown() {
        stableServer.stop();
        targetServer.stop();
    }

    @Test
    void smokeStageExecutesGetWithKoreanParamsAndPostMultilineJsonAndWritesReports(@TempDir Path tempDir) throws IOException {
        Path profile = setupTargetConfigs(tempDir, "catalog-kr", false);
        Path reportDir = tempDir.resolve("reports-smoke");

        targetServer.stubFor(get(urlPathEqualTo("/v1/catalog/categories"))
                .withQueryParam("locale", equalTo("ko_KR"))
                .withQueryParam("category", equalTo("전자제품"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "status": "OK",
                                  "categories": ["노트북", "스마트폰"],
                                  "code": "CAT-2026"
                                }
                                """)));

        targetServer.stubFor(post(urlPathEqualTo("/v1/catalog/items/batch"))
                .withRequestBody(equalToJson("""
                        { "skus": ["SKU-KR-1001", "SKU-KR-1002"], "region": "KR" }
                        """))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "items": [
                                    { "sku": "SKU-KR-1001", "currency": "KRW", "price": 1500000 },
                                    { "sku": "SKU-KR-1002", "currency": "KRW", "price": 890000 }
                                  ]
                                }
                                """)));

        RunnerEnvConfig env = testEnv(profile, reportDir);
        Nexus cli = new Nexus(env, null, null);
        StringWriter out = new StringWriter();
        CommandLine cmd = new CommandLine(cli);
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("smoke");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("[SMOKE] catalog-kr -> PASS");

        // Verify auth headers were sent to Target
        targetServer.verify(getRequestedFor(urlPathEqualTo("/v1/catalog/categories"))
                .withHeader("Authorization", equalTo("Bearer test-bearer"))
                .withHeader("X-Consumer-Token", equalTo("test-consumer")));
        targetServer.verify(postRequestedFor(urlPathEqualTo("/v1/catalog/items/batch"))
                .withHeader("Authorization", equalTo("Bearer test-bearer"))
                .withHeader("X-Consumer-Token", equalTo("test-consumer")));

        // Verify report.json
        String reportJson = Files.readString(reportDir.resolve("report.json"));
        assertThat(reportJson)
                .contains("\"smoke\" : {", "\"passed\" : true", "\"totalRequests\" : 2", "catalog-kr-categories-get");
    }

    @Test
    void regressionStageComparesStableAndTargetAndReportsFailures(@TempDir Path tempDir) throws IOException {
        Path profile = setupTargetConfigs(tempDir, "catalog-kr", false);
        Path reportDir = tempDir.resolve("reports-regression");

        stableServer.stubFor(get(urlPathEqualTo("/v1/catalog/items/SKU-1"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "request_id": "base-req", "timestamp": "t1", "price": 1000, "tags": ["a", "b"] }
                        """)));
        targetServer.stubFor(get(urlPathEqualTo("/v1/catalog/items/SKU-1"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "request_id": "stg-req", "timestamp": "t2", "price": 1000, "tags": ["a", "b"] }
                        """)));

        // Second request introduces a real regression in Target
        stableServer.stubFor(post(urlPathEqualTo("/v1/catalog/filter"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "request_id": "base-2", "total": 42 }
                        """)));
        targetServer.stubFor(post(urlPathEqualTo("/v1/catalog/filter"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "request_id": "stg-2", "total": 99 }
                        """)));

        RunnerEnvConfig env = testEnv(profile, reportDir);
        Nexus cli = new Nexus(env, null, null);
        StringWriter out = new StringWriter();
        CommandLine cmd = new CommandLine(cli);
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("regression");
        assertThat(exitCode).isEqualTo(1);
        assertThat(out.toString()).contains("[REGRESSION] catalog-kr -> FAIL (total=2, passed=1, failed=1");

        String reportJson = Files.readString(reportDir.resolve("report.json"));
        assertThat(reportJson)
                .contains("\"regression\" : {", "\"passed\" : false", "\"failedRequests\" : 1", "$.total");
    }

    @Test
    void diffStageReplaysKafkaMessagesWithTwitterDiffyAutoNoiseDetection(@TempDir Path tempDir) throws IOException {
        Path profile = setupTargetConfigs(tempDir, "catalog-kr", true);
        Path reportDirPass = tempDir.resolve("reports-diff-pass");
        Path reportDirFail = tempDir.resolve("reports-diff-fail");

        // Stable returns changing $.dynamic_nonce between call 1 and call 2 (auto-detected as noise)
        stableServer.stubFor(get(urlPathEqualTo("/v1/catalog/items/SKU-NOISE"))
                .inScenario("noise")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "dynamic_nonce": "nonce-b1", "price": 5000 }
                        """))
                .willSetStateTo("SECOND_CALL"));
        stableServer.stubFor(get(urlPathEqualTo("/v1/catalog/items/SKU-NOISE"))
                .inScenario("noise")
                .whenScenarioStateIs("SECOND_CALL")
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "dynamic_nonce": "nonce-b2", "price": 5000 }
                        """)));
        targetServer.stubFor(get(urlPathEqualTo("/v1/catalog/items/SKU-NOISE"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "dynamic_nonce": "nonce-stg", "price": 5000 }
                        """)));

        KafkaTrafficSource mockSource = cfg -> List.of(
                new SampledRequest("kafka-msg-1", new HttpRequestSpec(HttpMethod.GET, "/v1/catalog/items/SKU-NOISE", Map.of(), Map.of(), null))
        );

        Nexus passCli = new Nexus(testEnv(profile, reportDirPass), mockSource, null);
        CommandLine cmdPass = new CommandLine(passCli);
        cmdPass.setOut(new PrintWriter(new StringWriter()));
        int passExit = cmdPass.execute("diff");
        assertThat(passExit).isEqualTo(0);

        String reportJson = Files.readString(reportDirPass.resolve("report.json"));
        assertThat(reportJson).contains("\"diff\" : {").contains("$.dynamic_nonce");

        // Now test a real price mismatch that should still fail despite auto-noise detection
        stableServer.stubFor(post(urlPathEqualTo("/v1/catalog/quote"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "dynamic_nonce": "b-nonce", "price": 5000 }
                        """)));
        targetServer.stubFor(post(urlPathEqualTo("/v1/catalog/quote"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "dynamic_nonce": "s-nonce", "price": 6000 }
                        """)));

        var quoteBody = ConfigRepository.JSON_MAPPER.readTree("{\"sku\":\"SKU-NOISE\"}");
        KafkaTrafficSource failSource = cfg -> List.of(
                new SampledRequest("kafka-msg-2", new HttpRequestSpec(
                        HttpMethod.POST,
                        "/v1/catalog/quote",
                        Map.of(),
                        Map.of(),
                        quoteBody
                ))
        );

        Nexus failCli = new Nexus(testEnv(profile, reportDirFail), failSource, null);
        CommandLine cmdFail = new CommandLine(failCli);
        cmdFail.setOut(new PrintWriter(new StringWriter()));
        int failExit = cmdFail.execute("diff");
        assertThat(failExit).isEqualTo(1);
        assertThat(Files.readString(reportDirFail.resolve("report.json"))).contains("$.price");
    }

    @Test
    void doctorLiveChecklistChecksAllFourSlices(@TempDir Path tempDir) throws IOException {
        Path profile = setupTargetConfigs(tempDir, "catalog-tw", false);
        stableServer.stubFor(get(urlPathEqualTo("/actuator/health"))
                .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"UP\"}")));
        targetServer.stubFor(get(urlPathEqualTo("/actuator/health"))
                .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"UP\"}")));

        Doctor okKafka = (p, out) -> Doctor.logResult(
                out, "kafka", "catalog-tw", true, "Topic 'traffic.catalog.tw' reachable (3 partitions)");

        Nexus cli = new Nexus(testEnv(profile, tempDir.resolve("reports")), null, okKafka);
        StringWriter out = new StringWriter();
        CommandLine cmd = new CommandLine(cli);
        cmd.setOut(new PrintWriter(out));

        int exit = cmd.execute("doctor");
        assertThat(exit).isEqualTo(0);
        assertThat(out.toString())
                .contains("[PASS] config")
                .contains("[PASS] auth")
                .contains("[PASS] http-target")
                .contains("[PASS] http-stable")
                .contains("[PASS] kafka");
    }

    @Test
    void retriesFlakyFailureUntilPassAndFailsWhenRetriesExhausted(@TempDir Path tempDir) throws IOException {
        Path profile = setupTargetConfigs(tempDir, "catalog-kr", false);
        Path reportDirPass = tempDir.resolve("reports-retry-pass");
        Path reportDirFail = tempDir.resolve("reports-retry-fail");

        // Categories endpoint fails once with HTTP 500, then succeeds on attempt 2
        targetServer.stubFor(get(urlPathEqualTo("/v1/catalog/categories"))
                .inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"transient\"}"))
                .willSetStateTo("RECOVERED"));
        targetServer.stubFor(get(urlPathEqualTo("/v1/catalog/categories"))
                .inScenario("flaky")
                .whenScenarioStateIs("RECOVERED")
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "status": "OK", "categories": ["노트북"], "code": "CAT-1" }
                        """)));
        targetServer.stubFor(post(urlPathEqualTo("/v1/catalog/items/batch"))
                .willReturn(aResponse().withStatus(200).withBody("""
                        { "items": [{ "price": 1500000 }, { "price": 1200000 }] }
                        """)));

        Nexus passCli = new Nexus(testEnv(profile, reportDirPass), null, null);
        CommandLine passCmd = new CommandLine(passCli);
        passCmd.setOut(new PrintWriter(new StringWriter()));
        int passExit = passCmd.execute("smoke", "--retries", "1");
        assertThat(passExit).isEqualTo(0);

        // Always-failing endpoint must still fail even after retries are exhausted
        targetServer.stubFor(get(urlPathEqualTo("/v1/catalog/categories"))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"down\"}")));

        Nexus failCli = new Nexus(testEnv(profile, reportDirFail), null, null);
        CommandLine failCmd = new CommandLine(failCli);
        failCmd.setOut(new PrintWriter(new StringWriter()));
        int failExit = failCmd.execute("smoke", "--retries", "2");
        assertThat(failExit).isEqualTo(1);
    }

    private RunnerEnvConfig testEnv(Path profilePath, Path reportDir) {
        return new RunnerEnvConfig(
                profilePath,
                reportDir,
                4,
                "",
                "",
                "test-bearer",
                "test-consumer",
                "localhost:9092",
                Duration.ofSeconds(5),
                0
        );
    }

    private Path setupTargetConfigs(Path root, String name, boolean autoNoise) throws IOException {
        Path targetDir = root.resolve(name);
        Files.createDirectories(targetDir.resolve("smoke"));
        Files.createDirectories(targetDir.resolve("regression"));

        Path profileFile = targetDir.resolve("profile.yaml");
        Files.writeString(profileFile, String.format("""
                name: %s
                targetUrl: http://localhost:%d
                stableUrl: http://localhost:%d
                healthPath: /actuator/health
                diffRules:
                  ignoredPaths:
                    - $.request_id
                    - $.timestamp
                kafka:
                  topic: traffic.%s
                  maxMessages: 10
                  timeoutSeconds: 5
                  offsetReset: latest
                  autoNoiseDetection: %s
                """, name, targetServer.port(), stableServer.port(), name, autoNoise));

        Files.writeString(targetDir.resolve("smoke/01_categories.yaml"), """
                tests:
                  - id: catalog-kr-categories-get
                    description: Korean query param GET
                    request:
                      method: GET
                      path: /v1/catalog/categories
                      query:
                        locale: ko_KR
                        category: 전자제품
                    expect:
                      status: 200
                      assertions:
                        - path: $.status
                          op: equals
                          value: OK
                        - path: $.categories
                          op: notEmpty
                        - path: $.categories[0]
                          op: equals
                          value: 노트북
                        - path: $.code
                          op: notNull
                """);

        Files.writeString(targetDir.resolve("smoke/02_batch.yaml"), """
                tests:
                  - id: catalog-kr-batch-post
                    description: Inline JSON POST
                    request:
                      method: POST
                      path: /v1/catalog/items/batch
                      body: {
                        "skus": ["SKU-KR-1001", "SKU-KR-1002"],
                        "region": "KR"
                      }
                    expect:
                      status: 200
                      assertions:
                        - path: $.items
                          op: size
                          value: 2
                        - path: $.items[0].price
                          op: gt
                          value: 1000000
                """);

        Files.writeString(targetDir.resolve("regression/01_item.yaml"), """
                tests:
                  - id: reg-item-get
                    request:
                      method: GET
                      path: /v1/catalog/items/SKU-1
                """);

        Files.writeString(targetDir.resolve("regression/02_filter.yaml"), """
                tests:
                  - id: reg-filter-post
                    request:
                      method: POST
                      path: /v1/catalog/filter
                      body: { "category": "노트북" }
                """);
        return profileFile;
    }
}
