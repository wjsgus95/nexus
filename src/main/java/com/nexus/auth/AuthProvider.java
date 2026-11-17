package com.nexus.auth;

/**
 * Pluggable authentication provider so an internal company Vault/OAuth2 SDK can be swapped in seamlessly.
 */
public interface AuthProvider {
    AuthHeaders authenticate();

    String providerName();
}
