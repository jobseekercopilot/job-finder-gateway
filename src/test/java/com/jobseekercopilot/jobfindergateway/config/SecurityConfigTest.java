package com.jobseekercopilot.jobfindergateway.config;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityConfigTest {

    @Test
    void acceptsAbsoluteHttpAndHttpsJwksUris() {
        assertDoesNotThrow(() -> SecurityConfig.validateConfiguration(
                "https://auth.example.test/.well-known/jwks.json", "issuer", "audience"));
        assertDoesNotThrow(() -> SecurityConfig.validateConfiguration(
                "http://authentication-service:8084/.well-known/jwks.json",
                "issuer",
                "audience"));
    }

    @Test
    void rejectsMissingUnsafeAndIncompleteConfigurationWithoutEchoingValues() {
        for (String uri : List.of(
                "relative/jwks.json",
                "file:///private/key",
                "ftp://auth.example.test/jwks",
                "https://user:password@auth.example.test/jwks",
                "https://auth.example.test/jwks#fragment")) {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> SecurityConfig.validateConfiguration(uri, "issuer", "audience"));
            assertEquals(
                    "Job Finder JWT verification configuration is invalid",
                    exception.getMessage());
            assertFalse(exception.getMessage().contains(uri));
        }
        assertThrows(
                IllegalStateException.class,
                () -> SecurityConfig.validateConfiguration(null, "issuer", "audience"));
        assertThrows(
                IllegalStateException.class,
                () -> SecurityConfig.validateConfiguration(
                        "https://auth.example.test/jwks", " ", "audience"));
        assertThrows(
                IllegalStateException.class,
                () -> SecurityConfig.validateConfiguration(
                        "https://auth.example.test/jwks", "issuer", " "));
    }
}
