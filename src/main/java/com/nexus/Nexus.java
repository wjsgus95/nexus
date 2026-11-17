package com.nexus;

import com.nexus.auth.AuthProvider;
import com.nexus.auth.StubEnvAuthProvider;
import com.nexus.cli.DiffCommand;
import com.nexus.cli.DoctorCommand;
import com.nexus.cli.RegressionCommand;
import com.nexus.cli.SmokeCommand;
import com.nexus.config.RunnerEnvConfig;
import com.nexus.kafka.KafkaClientTrafficSource;
import com.nexus.kafka.KafkaTrafficSource;
import com.nexus.report.LatencySummary;
import com.nexus.report.Report;
import com.nexus.report.StageSummary;
import com.nexus.runner.doctor.Doctor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Root Picocli entry point for Nexus CI Smoke, Regression, Kafka Diff, and Doctor CLI.
 */
@Command(
        name = "nexus",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "CI Smoke, Regression & Kafka Traffic-Replay Diff CLI",
        subcommands = {
                SmokeCommand.class,
                RegressionCommand.class,
                DiffCommand.class,
                DoctorCommand.class
        }
)
public class Nexus implements Callable<Integer> {
    @Spec
    CommandSpec spec;

    private final RunnerEnvConfig baseEnvConfig;
    private final KafkaTrafficSource kafkaTrafficSourceOverride;
    private final Doctor kafkaDoctorOverride;

    public Nexus() {
        this(RunnerEnvConfig.fromEnv(), null, null);
    }

    public Nexus(
            RunnerEnvConfig baseEnvConfig,
            KafkaTrafficSource kafkaTrafficSourceOverride,
            Doctor kafkaDoctorOverride
    ) {
        this.baseEnvConfig = baseEnvConfig != null ? baseEnvConfig : RunnerEnvConfig.fromEnv();
        this.kafkaTrafficSourceOverride = kafkaTrafficSourceOverride;
        this.kafkaDoctorOverride = kafkaDoctorOverride;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Nexus()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }

    public RunnerEnvConfig effectiveEnv(Path profilePath, Path outputDir, Integer concurrency, Integer retries) {
        return baseEnvConfig.withOverrides(profilePath, outputDir, concurrency, retries);
    }

    public AuthProvider authProvider(RunnerEnvConfig env) {
        return new StubEnvAuthProvider(env);
    }

    public KafkaTrafficSource kafkaTrafficSource(RunnerEnvConfig env) {
        return kafkaTrafficSourceOverride != null
                ? kafkaTrafficSourceOverride
                : new KafkaClientTrafficSource(env.getKafkaBootstrapServers());
    }

    public Doctor getKafkaDoctorOverride() {
        return kafkaDoctorOverride;
    }

    public static int finishStage(
            PrintWriter out,
            String stageName,
            Report report,
            StageSummary summary,
            LatencySummary targetLatency,
            Path outputDir
    ) throws IOException {
        Path reportPath = report.writeJson(outputDir);
        out.printf("[%s] %s -> %s (total=%d, passed=%d, failed=%d, p95=%dms)%n",
                stageName.toUpperCase(),
                report.getName(),
                report.isPassed() ? "PASS" : "FAIL",
                summary.getTotalRequests(),
                summary.getPassedRequests(),
                summary.getFailedRequests(),
                targetLatency.getP95Ms());
        out.printf("Report written: %s%n", reportPath);
        out.flush();
        return report.isPassed() ? 0 : 1;
    }
}
