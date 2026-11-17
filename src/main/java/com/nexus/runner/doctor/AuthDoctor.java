package com.nexus.runner.doctor;

import com.nexus.auth.AuthHeaders;
import com.nexus.auth.AuthProvider;
import lombok.RequiredArgsConstructor;

import java.io.PrintWriter;
import java.nio.file.Path;

/**
 * Verifies auth token/header generation via the configured {@link AuthProvider}.
 */
@RequiredArgsConstructor
public class AuthDoctor implements Doctor {
    private final AuthProvider authProvider;

    @Override
    public boolean check(Path profileFile, PrintWriter out) {
        try {
            AuthHeaders authHeaders = authProvider.authenticate();
            boolean valid = authHeaders != null && !authHeaders.toHeaderMap().isEmpty();
            String detail = valid ? "Issued auth headers via " + authProvider.providerName() : "Auth headers were empty";
            return Doctor.logResult(out, "auth", authProvider.providerName(), valid, detail);
        } catch (Exception e) {
            return Doctor.logResult(out, "auth", authProvider.providerName(), false, e.getMessage());
        }
    }
}
