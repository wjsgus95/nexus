package com.nexus.cli;

import com.nexus.Nexus;
import com.nexus.config.ConfigRepository;
import com.nexus.config.RunnerEnvConfig;
import com.nexus.config.target.TargetConfig;
import com.nexus.report.Report;
import com.nexus.report.diff.DiffReport;
import com.nexus.runner.DiffRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "diff", description = "Stage 3: Replay sampled Kafka traffic concurrently against Stable and Target")
public class DiffCommand implements Callable<Integer> {
    @ParentCommand
    Nexus root;
    @Spec
    CommandSpec spec;
    @Mixin
    StageOptions options;

    @Option(names = "--auto-noise", negatable = true, description = "Override target autoNoiseDetection (Twitter Diffy double-stable)")
    Boolean autoNoise;

    @Override
    public Integer call() throws IOException {
        RunnerEnvConfig env = root.effectiveEnv(options.profile, options.outputDir, options.concurrency, options.retries);
        TargetConfig target = ConfigRepository.loadTarget(env.getProfilePath());
        boolean useAutoNoise = autoNoise != null ? autoNoise : target.getKafka().isAutoNoiseDetection();

        DiffReport diffReport = new DiffRunner(root.authProvider(env), root.kafkaTrafficSource(env))
                .run(target, env.getConcurrencyLimit(), useAutoNoise, env.getRequestTimeout(), env.getRetryCount());
        Report report = Report.builder()
                .name(target.getName())
                .diff(diffReport)
                .build();
        return Nexus.finishStage(
                spec.commandLine().getOut(),
                "diff",
                report,
                diffReport.getSummary(),
                diffReport.getTargetLatency(),
                env.getReportOutputDir()
        );
    }
}
