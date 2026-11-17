package com.nexus.runner.doctor;

import com.nexus.auth.AuthProvider;
import com.nexus.config.ConfigRepository;
import com.nexus.config.RunnerEnvConfig;
import com.nexus.config.target.DiffRulesConfig;
import com.nexus.config.target.TargetConfig;
import com.nexus.eval.diff.DiffResult;
import com.nexus.eval.diff.JsonDiffEngine;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a list of {@link Doctor} checks (offline or live), logging results directly, and runs local offline diff previews.
 */
@RequiredArgsConstructor
public class DoctorRunner {
    private final boolean offline;
    private final List<Doctor> doctors;

    public static DoctorRunner offline() {
        return new DoctorRunner(true, List.of(new ConfigDoctor()));
    }

    public static DoctorRunner live(
            RunnerEnvConfig envConfig,
            AuthProvider authProvider,
            Doctor kafkaDoctor
    ) {
        return new DoctorRunner(false, List.of(
                new ConfigDoctor(),
                new AuthDoctor(authProvider),
                new HttpDoctor("http-target", TargetConfig::getTargetUrl, authProvider, envConfig.getRequestTimeout()),
                new HttpDoctor("http-stable", TargetConfig::getStableUrl, authProvider, envConfig.getRequestTimeout()),
                kafkaDoctor != null ? kafkaDoctor : new KafkaDoctor(envConfig.getKafkaBootstrapServers())
        ));
    }

    public boolean run(Path profileFile, PrintWriter out) {
        out.println("Nexus Doctor (" + (offline ? "offline" : "live") + "):");
        boolean allPassed = !doctors.isEmpty();
        for (Doctor doctor : doctors) {
            allPassed &= doctor.check(profileFile, out);
        }
        out.flush();
        return allPassed;
    }

    public static DiffResult runDiff(
            Path stableFile,
            Path targetFile,
            Path profileFile,
            List<String> extraIgnoredPaths
    ) throws IOException {
        String stableJson = Files.readString(stableFile, StandardCharsets.UTF_8);
        String targetJson = Files.readString(targetFile, StandardCharsets.UTF_8);

        List<String> ignored = new ArrayList<>();
        if (profileFile != null) {
            TargetConfig target = ConfigRepository.loadTarget(profileFile);
            if (target.getDiffRules() != null && target.getDiffRules().getIgnoredPaths() != null) {
                ignored.addAll(target.getDiffRules().getIgnoredPaths());
            }
        }
        if (extraIgnoredPaths != null) {
            ignored.addAll(extraIgnoredPaths);
        }

        return JsonDiffEngine.diff(stableJson, targetJson, new DiffRulesConfig(ignored));
    }
}
