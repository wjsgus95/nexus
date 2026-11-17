package com.nexus.cli;

import com.nexus.Nexus;
import com.nexus.config.RunnerEnvConfig;
import com.nexus.runner.doctor.DoctorRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "doctor",
        description = "Pre-flight validation & live CI slice diagnostics",
        subcommands = {DoctorDiffCommand.class}
)
public class DoctorCommand implements Callable<Integer> {
    @ParentCommand
    Nexus root;
    @Spec
    CommandSpec spec;

    @Option(names = "--offline", description = "Run offline config, YAML, raw JSON body, and JsonPath validation with zero network calls")
    boolean offline;

    @Option(names = {"-p", "--profile"}, description = "Target profile YAML path (overrides PROFILE_PATH)")
    Path profile;

    @Override
    public Integer call() {
        RunnerEnvConfig env = root.effectiveEnv(profile, null, null, null);
        DoctorRunner runner = offline
                ? DoctorRunner.offline()
                : DoctorRunner.live(env, root.authProvider(env), root.getKafkaDoctorOverride());
        boolean passed = runner.run(env.getProfilePath(), spec.commandLine().getOut());
        return passed ? 0 : 1;
    }
}
