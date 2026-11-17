package com.nexus.runner.doctor;

import com.nexus.config.ConfigRepository;
import com.nexus.config.target.TargetConfig;
import com.nexus.config.testcase.TestCaseSpec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

/**
 * Validates target profile YAML syntax, schema fields, and sibling smoke/regression test suites.
 */
public class ConfigDoctor implements Doctor {

    @Override
    public boolean check(Path profileFile, PrintWriter out) {
        String label = profileFile != null ? profileFile.toString() : "profile";
        try {
            TargetConfig target = ConfigRepository.loadTarget(profileFile);
            label = target.getName();
            List<TestCaseSpec> smoke = ConfigRepository.loadTestCases(profileFile, "smoke");
            List<TestCaseSpec> regression = ConfigRepository.loadTestCases(profileFile, "regression");
            return Doctor.logResult(out, "config", label, true,
                    "Valid " + profileFile.getFileName() + " (" + smoke.size() + " smoke, " + regression.size() + " regression tests)");
        } catch (Exception e) {
            return Doctor.logResult(out, "config", label, false, e.getMessage());
        }
    }
}
