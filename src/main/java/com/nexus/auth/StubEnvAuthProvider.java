package com.nexus.auth;

import com.nexus.config.RunnerEnvConfig;
import lombok.RequiredArgsConstructor;

/**
 * Default stub/env-var AuthProvider reading AUTH_BEARER_TOKEN / AUTH_CONSUMER_TOKEN first
 * and falling back to deterministic stub tokens until replaced by the company Vault/OAuth SDK.
 */
@RequiredArgsConstructor
public class StubEnvAuthProvider implements AuthProvider {
    private static final String DEFAULT_BEARER_TOKEN = "stub-oauth-bearer-token";
    private static final String DEFAULT_CONSUMER_TOKEN = "stub-consumer-token";

    private final RunnerEnvConfig envConfig;

    @Override
    public AuthHeaders authenticate() {
        String bearer = hasText(envConfig.getAuthBearerToken())
                ? envConfig.getAuthBearerToken()
                : exchangeStubToken();
        String consumer = hasText(envConfig.getAuthConsumerToken())
                ? envConfig.getAuthConsumerToken()
                : DEFAULT_CONSUMER_TOKEN;
        return new AuthHeaders(bearer, consumer);
    }

    @Override
    public String providerName() {
        if (hasText(envConfig.getAuthBearerToken())) {
            return "env-token";
        }
        if (hasText(envConfig.getVaultAddr())) {
            return "stub-vault(" + envConfig.getVaultAddr() + ")";
        }
        return "stub-local";
    }

    private String exchangeStubToken() {
        if (hasText(envConfig.getVaultAddr()) && !hasText(envConfig.getVaultPath())) {
            throw new IllegalStateException("VAULT_ADDR is set without VAULT_PATH");
        }
        return DEFAULT_BEARER_TOKEN;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
