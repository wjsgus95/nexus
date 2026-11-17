package com.nexus.cli;

import com.nexus.Nexus;
import com.nexus.config.ConfigRepository;
import com.nexus.config.RunnerEnvConfig;
import com.nexus.config.target.TargetConfig;
import com.nexus.config.testcase.TestCaseSpec;
import com.nexus.report.Report;
import com.nexus.report.regression.RegressionReport;
import com.nexus.runner.RegressionRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "regression", description = "Stage 2: Run regression tests concurrently against Stable and Target with JSON diffing")
public class RegressionCommand implements Callable<Integer> {
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
        List<TestCaseSpec> tests = ConfigRepository.loadTestCases(env.getProfilePath(), "regression");

        RegressionReport regressionReport = new RegressionRunner(root.authProvider(env))
                .run(target, tests, env.getConcurrencyLimit(), env.getRequestTimeout(), env.getRetryCount());
        Report report = Report.builder()
                .name(target.getName())
                .regression(regressionReport)
                .build();
        return Nexus.finishStage(
                spec.commandLine().getOut(),
                "regression",
                report,
                regressionReport.getSummary(),
                regressionReport.getTargetLatency(),
                env.getReportOutputDir()
        );
    }
}
