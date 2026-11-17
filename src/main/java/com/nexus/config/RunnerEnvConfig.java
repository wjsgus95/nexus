package com.nexus.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

/**
 * Infrastructure and runner configuration loaded from environment variables with sensible defaults.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class RunnerEnvConfig {
    private final Path profilePath;
    private final Path reportOutputDir;
    private final int concurrencyLimit;
    private final String vaultAddr;
    private final String vaultPath;
    private final String authBearerToken;
    private final String authConsumerToken;
    private final String kafkaBootstrapServers;
    private final Duration requestTimeout;
    private final int retryCount;

    public static RunnerEnvConfig fromEnv() {
        Map<String, String> env = System.getenv();
        return RunnerEnvConfig.builder()
                .profilePath(Paths.get(getOrDefault(env, "PROFILE_PATH", "profile.yaml")))
                .reportOutputDir(Paths.get(getOrDefault(env, "REPORT_OUTPUT_DIR", "reports")))
                .concurrencyLimit(parsePositiveInt(getOrDefault(env, "CONCURRENCY_LIMIT", "10"), 10))
                .vaultAddr(env.getOrDefault("VAULT_ADDR", ""))
                .vaultPath(env.getOrDefault("VAULT_PATH", ""))
                .authBearerToken(env.getOrDefault("AUTH_BEARER_TOKEN", ""))
                .authConsumerToken(env.getOrDefault("AUTH_CONSUMER_TOKEN", ""))
                .kafkaBootstrapServers(env.getOrDefault("KAFKA_BOOTSTRAP_SERVERS", ""))
                .requestTimeout(Duration.ofMillis(parsePositiveLong(getOrDefault(env, "REQUEST_TIMEOUT_MS", "10000"), 10000L)))
                .retryCount(parseNonNegativeInt(getOrDefault(env, "RETRY_COUNT", "2"), 2))
                .build();
    }

    public RunnerEnvConfig withOverrides(
            Path profilePathOverride,
            Path reportOutputDirOverride,
            Integer concurrencyOverride,
            Integer retryOverride
    ) {
        return toBuilder()
                .profilePath(profilePathOverride != null ? profilePathOverride : this.profilePath)
                .reportOutputDir(reportOutputDirOverride != null ? reportOutputDirOverride : this.reportOutputDir)
                .concurrencyLimit(concurrencyOverride != null ? concurrencyOverride : this.concurrencyLimit)
                .retryCount(retryOverride != null ? Math.max(0, retryOverride) : this.retryCount)
                .build();
    }

    private static String getOrDefault(Map<String, String> env, String key, String defaultValue) {
        String val = env.get(key);
        return (val == null || val.isBlank()) ? defaultValue : val.trim();
    }

    private static int parsePositiveInt(String val, int fallback) {
        try {
            int parsed = Integer.parseInt(val.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseNonNegativeInt(String val, int fallback) {
        try {
            int parsed = Integer.parseInt(val.trim());
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parsePositiveLong(String val, long fallback) {
        try {
            long parsed = Long.parseLong(val.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
