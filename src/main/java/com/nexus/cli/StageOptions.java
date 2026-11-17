package com.nexus.cli;

import picocli.CommandLine.Option;

import java.nio.file.Path;

public class StageOptions {
    @Option(names = {"-p", "--profile"}, description = "Target profile YAML path (overrides PROFILE_PATH)")
    Path profile;

    @Option(names = "--output-dir", description = "Report output directory (overrides REPORT_OUTPUT_DIR)")
    Path outputDir;

    @Option(names = "--concurrency", description = "Concurrency limit (overrides CONCURRENCY_LIMIT)")
    Integer concurrency;

    @Option(names = "--retries", description = "Retry count per request on failure (overrides RETRY_COUNT)")
    Integer retries;
}
