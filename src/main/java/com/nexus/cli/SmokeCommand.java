package com.nexus.cli;

import com.nexus.Nexus;
import com.nexus.config.ConfigRepository;
import com.nexus.config.RunnerEnvConfig;
import com.nexus.config.target.TargetConfig;
import com.nexus.config.testcase.TestCaseSpec;
import com.nexus.report.Report;
import com.nexus.report.smoke.SmokeReport;
import com.nexus.runner.SmokeRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "smoke", description = "Stage 1: Run curated smoke tests against Target only")
public class SmokeCommand implements Callable<Integer> {
    @ParentCommand
    Nexus root;
    @Spec
    CommandSpec spec;
    @Mixin
    StageOptions options;

    @Override
    public Integer call() throws IOException {
        RunnerEnvConfig env = root.effectiveEnv(options.profile, options.outputDir, options.concurrency, options.retries);
        TargetConfig target = ConfigRepository.loadTarget(env.getProfilePath());
        List<TestCaseSpec> tests = ConfigRepository.loadTestCases(env.getProfilePath(), "smoke");

        SmokeReport smokeReport = new SmokeRunner(root.authProvider(env))
                .run(target, tests, env.getConcurrencyLimit(), env.getRequestTimeout(), env.getRetryCount());
        Report report = Report.builder()
                .name(target.getName())
                .smoke(smokeReport)
                .build();
        return Nexus.finishStage(
                spec.commandLine().getOut(),
                "smoke",
                report,
                smokeReport.getSummary(),
                smokeReport.getTargetLatency(),
                env.getReportOutputDir()
        );
    }
}
