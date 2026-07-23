package com.jobseekercopilot.jobfindergateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobFinderSecurityIntegrationTest {

    private static final TestJwksServer JWKS = new TestJwksServer();
    private static final TestDownstreamServer DOWNSTREAM = new TestDownstreamServer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("job-finder.security.jwk-set-uri", JWKS::jwkSetUri);
        registry.add("job-finder.security.issuer", () -> TestJwksServer.ISSUER);
        registry.add("job-finder.security.audience", () -> TestJwksServer.AUDIENCE);
        registry.add("services.user-profile.url", DOWNSTREAM::baseUrl);
        registry.add("services.job-service.url", DOWNSTREAM::baseUrl);
        registry.add("services.application-tracker.url", DOWNSTREAM::baseUrl);
        registry.add("services.document-store.url", DOWNSTREAM::baseUrl);
    }

    @AfterAll
    static void stopServers() {
        JWKS.close();
        DOWNSTREAM.close();
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetDownstreamEvidence() {
        DOWNSTREAM.reset();
    }

    @Test
    void validTokenBindsSubjectAndPreservesTheGeneratedJobServiceBoundary() throws Exception {
        String token = JWKS.validToken("alice");
        HttpHeaders headers = authenticated(token);
        headers.set("X-User-Id", "victim");

        ResponseEntity<Map> response = search(headers, """
                {
                  "aspirations": {
                    "desiredRoles": ["Platform Engineer"],
                    "locations": ["London"]
                  },
                  "workPreferences": {
                    "employmentType": ["FULL_TIME"]
                  },
                  "homeLocation": {
                    "displayName": "London",
                    "postcode": "SW1A 1AA",
                    "latitude": 51.501,
                    "longitude": -0.142
                  },
                  "selectedProviders": ["REED", "ADZUNA"]
                }
                """);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("alice", DOWNSTREAM.jobUserId());
        JsonNode forwarded = objectMapper.readTree(DOWNSTREAM.jobBody());
        assertEquals("SW1A 1AA", forwarded.at("/homeLocation/postcode").asText());
        assertEquals("REED", forwarded.at("/selectedProviders/0").asText());
        assertEquals("ADZUNA", forwarded.at("/selectedProviders/1").asText());
        assertEquals(
                "canonical-1",
                objectMapper.valueToTree(response.getBody()).at("/jobs/0/canonicalJobId").asText());
        assertEquals(
                "SUCCESS",
                objectMapper.valueToTree(response.getBody()).at("/providerResults/0/status").asText());
        assertNull(DOWNSTREAM.profileAuthorization());
    }

    @Test
    void profileLookupReceivesTheValidatedBearerToken() {
        String token = JWKS.validToken("profile-owner");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/jobs/search",
                HttpMethod.POST,
                new HttpEntity<>(authenticated(token)),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bearer " + token, DOWNSTREAM.profileAuthorization());
        assertNull(DOWNSTREAM.jobUserId());
    }

    @Test
    void missingMalformedExpiredForgedAndConstrainedTokensFailUniformly() {
        HttpHeaders spoofed = new HttpHeaders();
        spoofed.setContentType(MediaType.APPLICATION_JSON);
        spoofed.set("X-User-Id", "victim");
        assertAuthenticationFailure(spoofed, null);

        List<String> invalidTokens = List.of(
                "not-a-jwt",
                JWKS.expiredToken("expired-subject"),
                JWKS.forgedKnownKeyToken("forged-subject"),
                JWKS.unknownKeyToken("unknown-key-subject"),
                JWKS.wrongAlgorithmToken("wrong-algorithm-subject"),
                JWKS.wrongIssuerToken("wrong-issuer-subject"),
                JWKS.wrongAudienceToken("wrong-audience-subject"),
                JWKS.refreshTokenType("wrong-type-subject"));

        for (String token : invalidTokens) {
            assertAuthenticationFailure(authenticated(token), token);
        }
    }

    @Test
    void healthRemainsPublicAndUnrelatedRoutesAreDenied() {
        assertEquals(
                HttpStatus.OK,
                restTemplate.getForEntity("/actuator/health", Map.class).getStatusCode());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                restTemplate.getForEntity("/not-an-application-route", Map.class).getStatusCode());
    }

    private ResponseEntity<Map> search(HttpHeaders headers, String body) {
        return restTemplate.exchange(
                "/api/jobs/search",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    private void assertAuthenticationFailure(HttpHeaders headers, String token) {
        DOWNSTREAM.reset();
        headers.set("X-Correlation-Id", "job-finder-security-test");
        ResponseEntity<Map> response = search(headers, """
                {
                  "aspirations": {
                    "desiredRoles": ["Engineer"],
                    "locations": ["London"]
                  }
                }
                """);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("1", response.getBody().get("schemaVersion"));
        assertEquals("JOB_FINDER_AUTHENTICATION_REQUIRED", response.getBody().get("code"));
        assertEquals(
                "A valid Bearer access token is required.",
                response.getBody().get("message"));
        assertEquals("job-finder-security-test", response.getBody().get("correlationId"));
        if (token != null) {
            assertFalse(response.getBody().toString().contains(token));
        }
        assertFalse(response.getBody().toString().contains("subject"));
        assertFalse(response.getBody().toString().contains("Jwt"));
        assertNull(DOWNSTREAM.profileAuthorization());
        assertNull(DOWNSTREAM.jobUserId());
    }

    private static HttpHeaders authenticated(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static final class TestDownstreamServer implements AutoCloseable {

        private final AtomicReference<String> profileAuthorization = new AtomicReference<>();
        private final AtomicReference<String> jobUserId = new AtomicReference<>();
        private final AtomicReference<String> jobBody = new AtomicReference<>();
        private HttpServer server;

        synchronized String baseUrl() {
            if (server == null) {
                try {
                    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                    server.createContext("/api/profiles/me", this::profile);
                    server.createContext("/api/jobs/search", this::jobs);
                    server.start();
                } catch (IOException exception) {
                    throw new IllegalStateException(
                            "Could not start downstream test server", exception);
                }
            }
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void profile(HttpExchange exchange) throws IOException {
            profileAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{}");
        }

        private void jobs(HttpExchange exchange) throws IOException {
            jobUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            jobBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {
                      "jobs": [{
                        "id": "job-1",
                        "canonicalJobId": "canonical-1",
                        "title": "Platform Engineer",
                        "sources": []
                      }],
                      "resultsByTargetRole": [],
                      "totalResults": 1,
                      "page": 0,
                      "pageSize": 20,
                      "providerResults": [{
                        "provider": "REED",
                        "status": "SUCCESS",
                        "rawResultCount": 1
                      }]
                    }
                    """);
        }

        private static void respond(HttpExchange exchange, int status, String response)
                throws IOException {
            exchange.getRequestBody().close();
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        void reset() {
            profileAuthorization.set(null);
            jobUserId.set(null);
            jobBody.set(null);
        }

        String profileAuthorization() {
            return profileAuthorization.get();
        }

        String jobUserId() {
            return jobUserId.get();
        }

        String jobBody() {
            return jobBody.get();
        }

        @Override
        public synchronized void close() {
            if (server != null) {
                server.stop(0);
                server = null;
            }
        }
    }
}
