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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobFinderSecurityIntegrationTest {

    private static final UUID OWNED_APPLICATION =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FOREIGN_APPLICATION =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID UNKNOWN_APPLICATION =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
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
        assertEquals("Bearer " + token, DOWNSTREAM.jobAuthorization());
        assertNull(DOWNSTREAM.jobUserId());
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
        assertEquals(
                "COMPLETE",
                objectMapper.valueToTree(response.getBody()).at("/searchStatus").asText());
        assertEquals(
                "NOT_RUN",
                objectMapper.valueToTree(response.getBody()).at("/matchingStatus").asText());
        assertEquals(
                "2.0",
                objectMapper.valueToTree(response.getBody()).at("/jobs/0/canonicalSchemaVersion").asText());
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
        assertNull(DOWNSTREAM.jobAuthorization());
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

    @Test
    void applicationListUsesOnlyTheValidatedSubjectAndForwardsItsBearerToken() {
        String token = JWKS.validToken("alice");
        HttpHeaders headers = authenticated(token);
        headers.set("X-User-Id", "victim");

        ResponseEntity<List> response = restTemplate.exchange(
                "/api/jobs/applications/user/alice",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("alice", ((Map<?, ?>) response.getBody().get(0)).get("userId"));
        assertTrue(DOWNSTREAM.applicationCalls().stream().anyMatch(call ->
                call.method().equals("GET")
                        && call.path().equals("/api/v1/applications/user/alice")
                        && call.authorization().equals("Bearer " + token)));

        DOWNSTREAM.reset();
        ResponseEntity<Map> crossUser = restTemplate.exchange(
                "/api/jobs/applications/user/victim",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.NOT_FOUND, crossUser.getStatusCode());
        assertEquals("APPLICATION_NOT_FOUND", crossUser.getBody().get("error"));
        assertTrue(DOWNSTREAM.applicationCalls().isEmpty());
    }

    @Test
    void statusUpdateDeniesForeignAndUnknownIdsWithoutEnumeration() {
        String token = JWKS.validToken("alice");
        HttpEntity<String> request = new HttpEntity<>(
                "{\"status\":\"APPLIED\"}",
                authenticated(token));

        ResponseEntity<Map> foreign = restTemplate.exchange(
                "/api/jobs/applications/" + FOREIGN_APPLICATION + "/status",
                HttpMethod.PATCH,
                request,
                Map.class);
        ResponseEntity<Map> unknown = restTemplate.exchange(
                "/api/jobs/applications/" + UNKNOWN_APPLICATION + "/status",
                HttpMethod.PATCH,
                request,
                Map.class);

        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, unknown.getStatusCode());
        assertEquals(foreign.getBody(), unknown.getBody());
        assertEquals("APPLICATION_NOT_FOUND", foreign.getBody().get("error"));
        assertFalse(DOWNSTREAM.applicationCalls().stream().anyMatch(call ->
                call.method().equals("PATCH")));
        assertTrue(DOWNSTREAM.applicationCalls().stream().allMatch(call ->
                call.authorization().equals("Bearer " + token)));
    }

    @Test
    void ownedStatusAndWithdrawForwardBearerAndKeepDocumentCleanupOwnerScoped() {
        String token = JWKS.validToken("alice");

        ResponseEntity<Map> status = restTemplate.exchange(
                "/api/jobs/applications/" + OWNED_APPLICATION + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"applied\"}", authenticated(token)),
                Map.class);
        ResponseEntity<Map> withdraw = restTemplate.exchange(
                "/api/jobs/applications/" + OWNED_APPLICATION + "/withdraw-generated",
                HttpMethod.POST,
                new HttpEntity<>(authenticated(token)),
                Map.class);

        assertEquals(HttpStatus.OK, status.getStatusCode());
        assertEquals("alice", status.getBody().get("userId"));
        assertEquals("APPLIED", status.getBody().get("status"));
        assertEquals(HttpStatus.OK, withdraw.getStatusCode());
        assertEquals(Boolean.TRUE, withdraw.getBody().get("withdrawn"));
        assertTrue(DOWNSTREAM.applicationCalls().stream().anyMatch(call ->
                call.method().equals("PATCH")
                        && call.path().endsWith("/status")
                        && call.body().contains("\"status\":\"APPLIED\"")));
        assertTrue(DOWNSTREAM.applicationCalls().stream().anyMatch(call ->
                call.method().equals("POST")
                        && call.path().endsWith("/withdraw-generated")));
        assertEquals(2, DOWNSTREAM.documentCalls().size());
        assertTrue(DOWNSTREAM.applicationCalls().stream().allMatch(call ->
                call.authorization().equals("Bearer " + token)));
        assertTrue(DOWNSTREAM.documentCalls().stream().allMatch(call ->
                call.authorization().equals("Bearer " + token)));
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
        assertNull(DOWNSTREAM.jobAuthorization());
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
        private final AtomicReference<String> jobAuthorization = new AtomicReference<>();
        private final AtomicReference<String> jobUserId = new AtomicReference<>();
        private final AtomicReference<String> jobBody = new AtomicReference<>();
        private final List<DownstreamCall> applicationCalls = new CopyOnWriteArrayList<>();
        private final List<DownstreamCall> documentCalls = new CopyOnWriteArrayList<>();
        private HttpServer server;

        synchronized String baseUrl() {
            if (server == null) {
                try {
                    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                    server.createContext("/api/profiles/me", this::profile);
                    server.createContext("/api/jobs/search", this::jobs);
                    server.createContext("/api/v1/applications", this::applications);
                    server.createContext("/api/v1/documents", this::documents);
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
            jobAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            jobUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            jobBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {
                      "jobs": [{
                        "canonicalSchemaVersion": "2.0",
                        "canonicalJobId": "canonical-1",
                        "primarySource": "REED",
                        "externalJobId": "reed-1",
                        "title": "Platform Engineer",
                        "employmentTypeCode": "UNKNOWN",
                        "contractTypeCode": "UNKNOWN",
                        "workplaceType": "UNKNOWN",
                        "sources": [],
                        "skills": [],
                        "experience": {
                          "level": "UNKNOWN",
                          "normalisationStatus": "NOT_PROVIDED"
                        },
                        "fieldProvenance": []
                      }],
                      "resultsByTargetRole": [],
                      "totalResults": 1,
                      "page": 0,
                      "pageSize": 20,
                      "providerResults": [{
                        "provider": "REED",
                        "status": "SUCCESS",
                        "rawResultCount": 1
                      }],
                      "searchStatus": "COMPLETE",
                      "matchingStatus": "NOT_RUN"
                    }
                    """);
        }

        private void applications(HttpExchange exchange) throws IOException {
            DownstreamCall call = capture(exchange);
            applicationCalls.add(call);
            String path = call.path();

            if (call.method().equals("GET") && path.contains("/user/")) {
                respond(exchange, 200, "[" + application(
                        OWNED_APPLICATION,
                        path.substring(path.lastIndexOf('/') + 1),
                        "DOCUMENTS_GENERATED") + "]");
                return;
            }

            if (path.contains(UNKNOWN_APPLICATION.toString())) {
                respond(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }

            UUID id = path.contains(FOREIGN_APPLICATION.toString())
                    ? FOREIGN_APPLICATION
                    : OWNED_APPLICATION;
            String owner = id.equals(FOREIGN_APPLICATION) ? "mallory" : "alice";

            if (call.method().equals("GET")) {
                respond(exchange, 200, application(id, owner, "DOCUMENTS_GENERATED"));
                return;
            }
            if (call.method().equals("PATCH") && path.endsWith("/status")) {
                respond(exchange, 200, application(id, owner, "APPLIED"));
                return;
            }
            if (call.method().equals("POST") && path.endsWith("/withdraw-generated")) {
                respond(exchange, 200, """
                        {
                          "applicationId": "%s",
                          "status": "NEW",
                          "withdrawn": true,
                          "message": "Generated application withdrawn"
                        }
                        """.formatted(id));
                return;
            }
            respond(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
        }

        private void documents(HttpExchange exchange) throws IOException {
            documentCalls.add(capture(exchange));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }

        private DownstreamCall capture(HttpExchange exchange) throws IOException {
            return new DownstreamCall(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }

        private String application(UUID id, String owner, String status) {
            return """
                    {
                      "id": "%s",
                      "userId": "%s",
                      "jobId": "canonical-1",
                      "provider": "REED",
                      "externalJobId": "reed-1",
                      "jobTitle": "Platform Engineer",
                      "companyName": "Example Ltd",
                      "location": "London",
                      "cvDocumentId": "00000000-0000-0000-0000-000000000011",
                      "coverLetterDocumentId": "00000000-0000-0000-0000-000000000012",
                      "status": "%s"
                    }
                    """.formatted(id, owner, status);
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
            jobAuthorization.set(null);
            jobUserId.set(null);
            jobBody.set(null);
            applicationCalls.clear();
            documentCalls.clear();
        }

        String profileAuthorization() {
            return profileAuthorization.get();
        }

        String jobAuthorization() {
            return jobAuthorization.get();
        }

        String jobUserId() {
            return jobUserId.get();
        }

        String jobBody() {
            return jobBody.get();
        }

        List<DownstreamCall> applicationCalls() {
            return List.copyOf(applicationCalls);
        }

        List<DownstreamCall> documentCalls() {
            return List.copyOf(documentCalls);
        }

        @Override
        public synchronized void close() {
            if (server != null) {
                server.stop(0);
                server = null;
            }
        }
    }

    private record DownstreamCall(
            String method,
            String path,
            String authorization,
            String body) {
    }
}
