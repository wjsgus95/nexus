package com.nexus.runner.doctor;

import com.nexus.auth.AuthHeaders;
import com.nexus.auth.AuthProvider;
import com.nexus.config.ConfigRepository;
import com.nexus.config.target.TargetConfig;
import com.nexus.http.HttpCallResult;
import com.nexus.http.HttpMethod;
import com.nexus.http.HttpRequestSpec;
import lombok.RequiredArgsConstructor;

import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * Checks health endpoint HTTP reachability for an extracted URL (Target or Stable).
 */
@RequiredArgsConstructor
public class HttpDoctor implements Doctor {
    private final String slice;
    private final Function<TargetConfig, URI> urlExtractor;
    private final AuthProvider authProvider;
    private final Duration timeout;

    @Override
    public boolean check(Path profileFile, PrintWriter out) {
        TargetConfig target;
        try {
            target = ConfigRepository.loadTarget(profileFile);
        } catch (Exception ignored) {
            return true;
        }
        AuthHeaders authHeaders = null;
        try {
            authHeaders = authProvider.authenticate();
        } catch (Exception ignored) {
        }
        URI baseUrl = urlExtractor.apply(target);
        HttpRequestSpec healthReq = new HttpRequestSpec(HttpMethod.GET, target.getHealthPath(), Map.of(), Map.of(), null);
        HttpCallResult res = healthReq.execute(baseUrl, authHeaders, timeout);
        boolean ok = res.isTransportSuccess() && res.getStatusCode() >= 200 && res.getStatusCode() < 400;
        String detail = res.isTransportSuccess()
                ? baseUrl + healthReq.getPath() + " -> HTTP " + res.getStatusCode() + " (" + res.getLatencyMs() + "ms)"
                : baseUrl + healthReq.getPath() + " -> " + res.getError();
        return Doctor.logResult(out, slice, target.getName(), ok, detail);
    }
}
