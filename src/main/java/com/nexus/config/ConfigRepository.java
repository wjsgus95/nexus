package com.nexus.config;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.nexus.config.target.TargetConfig;
import com.nexus.config.testcase.AssertionSpec;
import com.nexus.config.testcase.TestCaseSpec;
import com.nexus.config.testcase.TestSuiteConfig;
import com.nexus.http.HttpRequestSpec;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads and offline-validates a target profile YAML and its sibling smoke/regression test suites.
 */
public class ConfigRepository {
    public static final ObjectMapper JSON_MAPPER = createJsonMapper();
    public static final ObjectMapper PRETTY_JSON_MAPPER = JSON_MAPPER.copy()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public static final ObjectMapper YAML_MAPPER = YAMLMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .defaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP))
            .build();

    private static ObjectMapper createJsonMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .defaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP))
                .build();
    }

    public static TargetConfig loadTarget(Path profileFile) {
        if (profileFile == null || !Files.isRegularFile(profileFile)) {
            throw new ConfigValidationException(profileFile, "Target profile file not found");
        }
        TargetConfig config = readYaml(profileFile, TargetConfig.class);
        validateTarget(config, profileFile);
        return config;
    }

    public static List<TestSuiteConfig> loadSuites(Path profileFile, String stageDirName) {
        Path targetDir = profileFile.toAbsolutePath().getParent();
        Path stageDir = targetDir != null ? targetDir.resolve(stageDirName) : Path.of(stageDirName);
        if (!Files.isDirectory(stageDir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(stageDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".yaml"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new ConfigValidationException(stageDir, "Failed to list test suite files: " + e.getMessage(), e);
        }

        List<TestSuiteConfig> suites = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (Path file : files) {
            TestSuiteConfig suite = readYaml(file, TestSuiteConfig.class);
            validateSuite(suite, file, seenIds);
            suites.add(suite);
        }
        return suites;
    }

    public static List<TestCaseSpec> loadTestCases(Path profileFile, String stageDirName) {
        return loadSuites(profileFile, stageDirName).stream()
                .flatMap(suite -> suite.getTests().stream())
                .toList();
    }

    private static <T> T readYaml(Path file, Class<T> type) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                throw new ConfigValidationException(file, "Configuration file is empty");
            }
            T parsed = YAML_MAPPER.readValue(content, type);
            if (parsed == null) {
                throw new ConfigValidationException(file, "Parsed empty " + type.getSimpleName());
            }
            return parsed;
        } catch (IOException e) {
            throw new ConfigValidationException(file, "Failed to parse " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static void validateTarget(TargetConfig config, Path file) {
        requireText(config.getName(), file, "'name' must not be blank");
        requireUri(config.getTargetUrl(), file, "'targetUrl' must not be blank");
        requireUri(config.getStableUrl(), file, "'stableUrl' must not be blank");

        if (config.getDiffRules() != null && config.getDiffRules().getIgnoredPaths() != null) {
            for (String ignoredPath : config.getDiffRules().getIgnoredPaths()) {
                validateJsonPath(ignoredPath, file, "diffRules.ignoredPaths");
            }
        }
        if (config.getKafka() != null && config.getKafka().getOffsetReset() != null) {
            String offset = config.getKafka().getOffsetReset().toLowerCase();
            if (!offset.equals("earliest") && !offset.equals("latest")) {
                throw new ConfigValidationException(file,
                        "kafka.offsetReset must be 'earliest' or 'latest', got: " + config.getKafka().getOffsetReset());
            }
        }
    }

    private static void validateSuite(TestSuiteConfig suite, Path file, Set<String> seenIds) {
        if (suite.getTests() == null || suite.getTests().isEmpty()) {
            throw new ConfigValidationException(file, "Test suite must contain at least one test case");
        }
        for (int i = 0; i < suite.getTests().size(); i++) {
            TestCaseSpec tc = suite.getTests().get(i);
            requireText(tc.getId(), file, "Test case at index " + i + " is missing 'id'");
            if (seenIds != null && !seenIds.add(tc.getId())) {
                throw new ConfigValidationException(file, "Duplicate test case id: '" + tc.getId() + "'");
            }
            HttpRequestSpec req = tc.getRequest();
            if (req == null) {
                throw new ConfigValidationException(file, "Test '" + tc.getId() + "' is missing 'request'");
            }
            if (req.getMethod() == null) {
                throw new ConfigValidationException(file, "Test '" + tc.getId() + "' has unsupported HTTP method");
            }
            if (req.getPath() == null || !req.getPath().startsWith("/")) {
                throw new ConfigValidationException(file, "Test '" + tc.getId() + "' request.path must start with '/': " + req.getPath());
            }
            if (req.getBody() != null && !req.getBody().isNull() && !req.getBody().isContainerNode()) {
                throw new ConfigValidationException(file, "Test '" + tc.getId() + "' request.body must be a JSON object or array");
            }
            if (tc.getExpect() != null) {
                int expectedStatus = tc.getExpect().getStatus() != null ? tc.getExpect().getStatus() : 200;
                if (expectedStatus < 100 || expectedStatus > 599) {
                    throw new ConfigValidationException(file, "Test '" + tc.getId() + "' invalid expected status: " + expectedStatus);
                }
                List<AssertionSpec> assertions = tc.getExpect().getAssertions();
                if (assertions != null) {
                    for (AssertionSpec assertion : assertions) {
                        validateJsonPath(assertion.getPath(), file, "test '" + tc.getId() + "' assertion.path");
                        if (assertion.getOp() == null) {
                            throw new ConfigValidationException(file, "Test '" + tc.getId() + "' assertion.op must not be null");
                        }
                    }
                }
            }
        }
    }

    private static void requireText(String value, Path file, String message) {
        if (value == null || value.isBlank()) {
            throw new ConfigValidationException(file, message);
        }
    }

    private static void requireUri(URI uri, Path file, String message) {
        if (uri == null || uri.toString().isBlank()) {
            throw new ConfigValidationException(file, message);
        }
    }

    public static void validateJsonPath(String pathExpr, Path sourceFile, String context) {
        if (pathExpr == null || pathExpr.isBlank()) {
            throw new ConfigValidationException(sourceFile, "Empty JsonPath in " + context);
        }
        try {
            JsonPath.compile(normalizeJsonPath(pathExpr));
        } catch (InvalidPathException | IllegalArgumentException e) {
            throw new ConfigValidationException(sourceFile,
                    "Invalid JsonPath '" + pathExpr + "' in " + context + ": " + e.getMessage(), e);
        }
    }

    public static String normalizeJsonPath(String pathExpr) {
        String trimmed = pathExpr.trim();
        if (trimmed.startsWith("$")) {
            return trimmed;
        }
        return trimmed.startsWith(".") ? "$" + trimmed : "$." + trimmed;
    }
}
