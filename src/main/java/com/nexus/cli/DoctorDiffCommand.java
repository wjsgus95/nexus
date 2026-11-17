package com.nexus.cli;

import com.nexus.eval.diff.DiffResult;
import com.nexus.eval.diff.FieldDiff;
import com.nexus.runner.doctor.DoctorRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "diff", description = "Offline diff preview comparing two local JSON files using target diff rules")
public class DoctorDiffCommand implements Callable<Integer> {
    @ParentCommand
    DoctorCommand parent;
    @Spec
    CommandSpec spec;

    @Parameters(index = "0", description = "Stable JSON file path")
    Path stableFile;

    @Parameters(index = "1", description = "Target JSON file path")
    Path targetFile;

    @Option(names = {"-p", "--profile"}, description = "Optional profile YAML path to load shared diffRules")
    Path profile;

    @Option(names = "--ignore", description = "Additional JsonPath(s) to ignore")
    List<String> extraIgnores = new ArrayList<>();

    @Override
    public Integer call() throws IOException {
        Path effectiveProfile = profile != null ? profile : parent.profile;
        DiffResult result = DoctorRunner.runDiff(
                stableFile, targetFile, effectiveProfile, extraIgnores
        );

        PrintWriter out = spec.commandLine().getOut();
        if (result.isIdentical()) {
            out.println("PASS: No JSON differences found.");
        } else {
            out.println("FAIL: Found " + result.getDiffs().size() + " JSON difference(s):");
            for (FieldDiff fd : result.getDiffs()) {
                out.printf("  - %s: stable=%s | target=%s%n",
                        fd.getPath(), fd.getStableValue(), fd.getTargetValue());
            }
        }
        out.flush();
        return result.isIdentical() ? 0 : 1;
    }
}
