package com.jobseekercopilot.jobfindergateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
    private static final UUID PENDING_WITHDRAWAL_APPLICATION =
            UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID SAVED_JOB =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID MISSING_SAVED_JOB =
            UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_OWNER_SAVED_JOB =
            UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID UNAVAILABLE_SAVED_JOB =
            UUID.fromString("10000000-0000-4000-8000-000000000004");
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
        registry.add("job-finder.downstream.connect-timeout-ms", () -> 100);
        registry.add("job-finder.downstream.connection-request-timeout-ms", () -> 100);
        registry.add("job-finder.downstream.response-timeout-ms", () -> 500);
        registry.add("job-finder.downstream.request-deadline-ms", () -> 700);
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

    @LocalServerPort
    private int serverPort;

    @BeforeEach
    void resetDownstreamEvidence() {
        DOWNSTREAM.reset();
    }

    @Test
    void validTokenBindsSubjectAndPreservesTheGeneratedJobServiceBoundary() throws Exception {
        String token = JWKS.validToken("alice");
        HttpHeaders headers = authenticated(token);
        headers.set("X-User-Id", "victim");
        headers.set("X-Correlation-Id", "search-boundary");

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
                  "selectedProviders": ["REED", "ADZUNA"],
                  "page": 2,
                  "pageSize": 20,
                  "sort": "NEWEST_POSTED"
                }
                """);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Bearer " + token, DOWNSTREAM.jobAuthorization());
        assertEquals("search-boundary", DOWNSTREAM.jobCorrelationId());
        assertNull(DOWNSTREAM.jobUserId());
        JsonNode forwarded = objectMapper.readTree(DOWNSTREAM.jobBody());
        assertEquals("SW1A 1AA", forwarded.at("/homeLocation/postcode").asText());
        assertEquals("REED", forwarded.at("/selectedProviders/0").asText());
        assertEquals("ADZUNA", forwarded.at("/selectedProviders/1").asText());
        assertEquals(2, forwarded.at("/page").asInt());
        assertEquals(20, forwarded.at("/pageSize").asInt());
        assertEquals("NEWEST_POSTED", forwarded.at("/sort").asText());
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
                "Platform Engineer",
                objectMapper.valueToTree(response.getBody())
                        .at("/resultsByTargetRole/0/targetRole")
                        .asText());
        assertEquals(
                "canonical-1",
                objectMapper.valueToTree(response.getBody())
                        .at("/resultsByTargetRole/0/jobs/0/canonicalJobId")
                        .asText());
        assertEquals(
                37,
                objectMapper.valueToTree(response.getBody())
                        .at("/resultsByTargetRole/0/totalResults")
                        .asInt());
        assertEquals(
                2,
                objectMapper.valueToTree(response.getBody())
                        .at("/resultsByTargetRole/0/page")
                        .asInt());
        assertEquals(
                20,
                objectMapper.valueToTree(response.getBody())
                        .at("/resultsByTargetRole/0/pageSize")
                        .asInt());
        assertEquals(
                2,
                objectMapper.valueToTree(response.getBody())
                        .at("/resultsByTargetRole/0/totalPages")
                        .asInt());
        assertEquals(
                "TIMED_OUT",
                objectMapper.valueToTree(response.getBody())
                        .at("/resultsByTargetRole/0/providerResults/1/status")
                        .asText());
        assertEquals(
                "PARTIAL",
                objectMapper.valueToTree(response.getBody())
                        .at("/resultsByTargetRole/0/searchStatus")
                        .asText());
        assertEquals(
                "COMPLETE",
                objectMapper.valueToTree(response.getBody())
                        .at("/resultsByTargetRole/0/matchingStatus")
                        .asText());
        assertEquals(
                "2.0",
                objectMapper.valueToTree(response.getBody()).at("/jobs/0/canonicalSchemaVersion").asText());
        assertEquals(
                5,
                objectMapper.valueToTree(response.getBody()).at("/totalPages").asInt());
        assertEquals(
                "NEWEST_POSTED",
                objectMapper.valueToTree(response.getBody()).at("/sort").asText());
        assertNull(DOWNSTREAM.profileAuthorization());
    }

    @Test
    void selectedJobDetailsForwardBearerAndReturnCompleteDescription() {
        String token = JWKS.validToken("job-detail-owner");
        HttpHeaders headers = authenticated(token);
        headers.set("X-Correlation-Id", "job-details-boundary");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/jobs/provider/reed/reed-1",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Bearer " + token, DOWNSTREAM.jobAuthorization());
        assertEquals("job-details-boundary", DOWNSTREAM.jobCorrelationId());
        assertNull(DOWNSTREAM.jobUserId());
        JsonNode body = objectMapper.valueToTree(response.getBody());
        assertEquals("FULL", body.at("/descriptionCompleteness").asText());
        assertEquals(
                "Complete provider job description.",
                body.at("/description").asText());
    }

    @Test
    void omittedPagingFieldsRemainUnsetForJobServiceDefaults() throws Exception {
        ResponseEntity<Map> response = search(
                authenticated(JWKS.validToken("default-paging-user")),
                """
                {
                  "aspirations": {
                    "desiredRoles": ["Platform Engineer"],
                    "locations": ["London"]
                  }
                }
                """);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode forwarded = objectMapper.readTree(DOWNSTREAM.jobBody());
        assertTrue(forwarded.at("/page").isMissingNode()
                || forwarded.at("/page").isNull());
        assertTrue(forwarded.at("/pageSize").isMissingNode()
                || forwarded.at("/pageSize").isNull());
        assertTrue(forwarded.at("/sort").isMissingNode()
                || forwarded.at("/sort").isNull());
    }

    @Test
    void invalidSearchBoundariesReturnStableErrorsWithoutCallingDownstreams() {
        List<String> invalidBodies = List.of(
                "{}",
                """
                {"aspirations":{"desiredRoles":[],"locations":["London"]}}
                """,
                """
                {"aspirations":{"desiredRoles":["Engineer"],"locations":[]}}
                """,
                """
                {"aspirations":{"desiredRoles":["Engineer"],"locations":["London"]},"page":0}
                """,
                """
                {"aspirations":{"desiredRoles":["Engineer"],"locations":["London"]},"page":101}
                """,
                """
                {"aspirations":{"desiredRoles":["Engineer"],"locations":["London"]},"pageSize":0}
                """,
                """
                {"aspirations":{"desiredRoles":["Engineer"],"locations":["London"]},"pageSize":51}
                """,
                """
                {"aspirations":{"desiredRoles":["Engineer"],"locations":["London"]},"sort":"RANDOM"}
                """,
                """
                {"aspirations":{"desiredRoles":["Engineer"],"locations":["London"]},"selectedProviders":["UNKNOWN"]}
                """,
                """
                {
                  "aspirations":{"desiredRoles":["Engineer"],"locations":["London"]},
                  "workPreferences":{"employmentType":["PERMANENT"]}
                }
                """,
                """
                {
                  "aspirations":{"desiredRoles":["Engineer"],"locations":["London"]},
                  "homeLocation":{"latitude":91}
                }
                """,
                """
                {
                  "aspirations":{
                    "desiredRoles":["Engineer"],
                    "locations":["London"],
                    "salaryExpectation":{"min":50000,"max":40000,"currency":"GBP"}
                  }
                }
                """,
                """
                {
                  "aspirations":{
                    "desiredRoles":[
                      "1","2","3","4","5","6","7","8","9","10","11"
                    ],
                    "locations":["London"]
                  }
                }
                """,
                """
                {
                  "aspirations":{"desiredRoles":["%s"],"locations":["London"]}
                }
                """.formatted("x".repeat(121)),
                """
                {
                  "aspirations":{
                    "desiredRoles":["Engineer"],
                    "locations":["1","2","3","4","5","6","7","8","9","10","11"]
                  }
                }
                """,
                """
                {
                  "aspirations":{"desiredRoles":["Engineer"],"locations":["London"]},
                  "workPreferences":{"remotePreference":"ANYWHERE"}
                }
                """,
                """
                {
                  "aspirations":{
                    "desiredRoles":["Engineer"],
                    "locations":["London"],
                    "salaryExpectation":{"min":0,"max":50000,"currency":"POUNDS"}
                  }
                }
                """);

        for (int index = 0; index < invalidBodies.size(); index++) {
            DOWNSTREAM.reset();
            String correlationId = "validation-" + index;
            HttpHeaders headers = authenticated(JWKS.validToken("invalid-search-" + index));
            headers.set("X-Correlation-Id", correlationId);

            ResponseEntity<Map> response = search(headers, invalidBodies.get(index));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("1", response.getBody().get("schemaVersion"));
            assertEquals("JOB_FINDER_INVALID_REQUEST", response.getBody().get("code"));
            assertEquals(
                    "The request does not meet the documented requirements.",
                    response.getBody().get("message"));
            assertEquals(correlationId, response.getBody().get("correlationId"));
            assertEquals(correlationId, response.getHeaders().getFirst("X-Correlation-Id"));
            assertNull(DOWNSTREAM.profileAuthorization());
            assertNull(DOWNSTREAM.jobAuthorization());
            assertNull(DOWNSTREAM.jobBody());
        }
    }

    @Test
    void malformedJsonReturnsStableErrorWithoutCallingDownstreams() {
        HttpHeaders headers = authenticated(JWKS.validToken("malformed-search"));
        headers.set("X-Correlation-Id", "malformed-json");

        ResponseEntity<Map> response = search(headers, "{\"aspirations\":");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("1", response.getBody().get("schemaVersion"));
        assertEquals("JOB_FINDER_MALFORMED_JSON", response.getBody().get("code"));
        assertEquals("malformed-json", response.getBody().get("correlationId"));
        assertNull(DOWNSTREAM.profileAuthorization());
        assertNull(DOWNSTREAM.jobAuthorization());
        assertNull(DOWNSTREAM.jobBody());
    }

    @Test
    void fixedLengthOversizedSearchBodyReturnsPayloadTooLarge() {
        HttpHeaders headers = authenticated(JWKS.validToken("oversized-fixed-search"));
        headers.set("X-Correlation-Id", "oversized-fixed");
        String body = "{\"padding\":\"" + "x".repeat(65_536) + "\"}";

        ResponseEntity<Map> response = search(headers, body);

        assertPayloadTooLarge(response.getStatusCode(), response.getBody(), "oversized-fixed");
        assertNull(DOWNSTREAM.profileAuthorization());
        assertNull(DOWNSTREAM.jobAuthorization());
        assertNull(DOWNSTREAM.jobBody());
    }

    @Test
    void streamedOversizedSearchBodyReturnsPayloadTooLarge() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + serverPort + "/api/jobs/search")
                .toURL()
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(1_024);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        connection.setRequestProperty(
                "Authorization",
                "Bearer " + JWKS.validToken("oversized-stream-search"));
        connection.setRequestProperty("X-Correlation-Id", "oversized-stream");
        byte[] body = ("{\"padding\":\"" + "x".repeat(65_536) + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), connection.getResponseCode());
        try (InputStream responseBody = connection.getErrorStream()) {
            JsonNode error = objectMapper.readTree(responseBody);
            assertEquals("1", error.at("/schemaVersion").asText());
            assertEquals("JOB_SEARCH_PAYLOAD_TOO_LARGE", error.at("/code").asText());
            assertEquals("oversized-stream", error.at("/correlationId").asText());
        } finally {
            connection.disconnect();
        }
        assertNull(DOWNSTREAM.profileAuthorization());
        assertNull(DOWNSTREAM.jobAuthorization());
        assertNull(DOWNSTREAM.jobBody());
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
        assertEquals("1", response.getBody().get("schemaVersion"));
        assertEquals("JOB_FINDER_INVALID_PROFILE", response.getBody().get("code"));
        assertEquals(
                response.getHeaders().getFirst("X-Correlation-Id"),
                response.getBody().get("correlationId"));
        assertEquals("Bearer " + token, DOWNSTREAM.profileAuthorization());
        assertNull(DOWNSTREAM.jobAuthorization());
        assertNull(DOWNSTREAM.jobUserId());
    }

    @Test
    void profileTimeoutExhaustsTheSharedBudgetWithoutStartingJobSearch() {
        DOWNSTREAM.setProfileDelayMs(900);
        HttpHeaders headers = authenticated(JWKS.validToken("slow-profile"));
        headers.set("X-Correlation-Id", "slow-profile-request");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/jobs/search",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals("1", response.getBody().get("schemaVersion"));
        assertEquals("JOB_FINDER_PROFILE_TIMEOUT", response.getBody().get("code"));
        assertEquals("slow-profile-request", response.getBody().get("correlationId"));
        assertEquals("slow-profile-request", DOWNSTREAM.profileCorrelationId());
        assertNull(DOWNSTREAM.jobAuthorization());
    }

    @Test
    void malformedJobServiceResponseReturnsStableBadGatewayError() {
        DOWNSTREAM.setMalformedJobResponse(true);
        HttpHeaders headers = authenticated(JWKS.validToken("malformed-downstream"));
        headers.set("X-Correlation-Id", "malformed-job-response");

        ResponseEntity<Map> response = search(headers, """
                {
                  "aspirations": {
                    "desiredRoles": ["Platform Engineer"],
                    "locations": ["London"]
                  }
                }
                """);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("1", response.getBody().get("schemaVersion"));
        assertEquals(
                "JOB_FINDER_INVALID_JOB_SERVICE_RESPONSE",
                response.getBody().get("code"));
        assertEquals("malformed-job-response", response.getBody().get("correlationId"));
        assertFalse(response.getBody().toString().contains("Json"));
    }

    @Test
    void savedJobBoundaryPreservesOwnerTokenOutcomeAndServerIdentity() {
        String token = JWKS.validToken("saved-owner");
        HttpHeaders headers = authenticated(token);
        headers.set("X-User-Id", "victim");
        HttpEntity<String> request = new HttpEntity<>(canonicalJob(), headers);

        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/jobs/saved",
                HttpMethod.POST,
                request,
                Map.class);
        ResponseEntity<Map> replayed = restTemplate.exchange(
                "/api/jobs/saved",
                HttpMethod.POST,
                request,
                Map.class);
        ResponseEntity<Map> listed = restTemplate.exchange(
                "/api/jobs/saved?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        ResponseEntity<Map> retrieved = restTemplate.exchange(
                "/api/jobs/saved/" + SAVED_JOB,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        ResponseEntity<Void> unsaved = restTemplate.exchange(
                "/api/jobs/saved/" + SAVED_JOB,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("CREATED", created.getHeaders().getFirst("X-Saved-Job-Outcome"));
        assertEquals(SAVED_JOB.toString(), created.getBody().get("savedJobId"));
        assertEquals(HttpStatus.OK, replayed.getStatusCode());
        assertEquals("REPLAYED", replayed.getHeaders().getFirst("X-Saved-Job-Outcome"));
        assertEquals(created.getBody(), replayed.getBody());
        assertEquals(
                SAVED_JOB.toString(),
                objectMapper.valueToTree(listed.getBody()).at("/items/0/savedJobId").asText());
        assertEquals(SAVED_JOB.toString(), retrieved.getBody().get("savedJobId"));
        assertEquals(
                "canonical-1",
                objectMapper.valueToTree(retrieved.getBody()).at("/job/canonicalJobId").asText());
        assertEquals(HttpStatus.NO_CONTENT, unsaved.getStatusCode());

        assertEquals(5, DOWNSTREAM.savedJobCalls().size());
        assertTrue(DOWNSTREAM.savedJobCalls().stream().allMatch(call ->
                call.authorization().equals("Bearer " + token)
                        && call.userId() == null));
        assertTrue(DOWNSTREAM.savedJobCalls().stream()
                .filter(call -> call.method().equals("POST"))
                .allMatch(call -> call.body().contains("\"canonicalJobId\":\"canonical-1\"")));
    }

    @Test
    void savedJobMissingAndOtherOwnerIdsAreNonEnumerating() {
        HttpHeaders headers = authenticated(JWKS.validToken("saved-owner"));

        ResponseEntity<Map> missing = restTemplate.exchange(
                "/api/jobs/saved/" + MISSING_SAVED_JOB,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        ResponseEntity<Map> otherOwner = restTemplate.exchange(
                "/api/jobs/saved/" + OTHER_OWNER_SAVED_JOB,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, otherOwner.getStatusCode());
        assertEquals(missing.getBody().get("code"), otherOwner.getBody().get("code"));
        assertEquals(missing.getBody().get("message"), otherOwner.getBody().get("message"));
        assertEquals("SAVED_JOB_NOT_FOUND", missing.getBody().get("code"));
        assertEquals("1", missing.getBody().get("schemaVersion"));
        assertTrue(missing.getBody().containsKey("correlationId"));
    }

    @Test
    void savedJobDependencyFailureIsStableAndDoesNotLeakUpstreamDetail() {
        HttpHeaders headers = authenticated(JWKS.validToken("saved-owner"));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/jobs/saved/" + UNAVAILABLE_SAVED_JOB,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(
                "JOB_FINDER_SAVED_JOB_UNAVAILABLE",
                response.getBody().get("code"));
        assertEquals("1", response.getBody().get("schemaVersion"));
        assertTrue(response.getBody().containsKey("correlationId"));
        assertFalse(response.getBody().toString().contains("database-host"));
    }

    @Test
    void savedJobRoutesRequireAuthenticationBeforeCallingJobService() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/jobs/saved",
                HttpMethod.POST,
                new HttpEntity<>(canonicalJob()),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(DOWNSTREAM.savedJobCalls().isEmpty());
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
        assertEquals(
                "canonical-1",
                ((Map<?, ?>) response.getBody().get(0)).get("canonicalJobId"));
        assertEquals(
                "GENERATED",
                ((Map<?, ?>) response.getBody().get(0)).get("provenance"));
        assertEquals(
                2,
                ((Number) ((Map<?, ?>) response.getBody().get(0)).get("version")).intValue());
        assertEquals(
                "UNKNOWN",
                ((Map<?, ?>) response.getBody().get(0)).get("applicationUsedCvState"));
        assertEquals(
                "UNKNOWN",
                ((Map<?, ?>) response.getBody().get(0)).get("applicationUsedCoverLetterState"));
        assertEquals(
                "33333333-3333-4333-8333-333333333333",
                objectMapper.valueToTree(response.getBody())
                        .at("/0/cvDocumentReference/evidenceProvenance/profileRevisionId")
                        .asText());
        assertEquals(
                "UPLOADED",
                objectMapper.valueToTree(response.getBody())
                        .at("/0/cvDocumentReference/sourceType")
                        .asText());
        assertEquals(
                "abababababababababababababababababababababababababababababababab",
                objectMapper.valueToTree(response.getBody())
                        .at("/0/cvDocumentReference/originalContentSha256")
                        .asText());
        assertEquals(
                "2026-07-29T03:01:00",
                objectMapper.valueToTree(response.getBody())
                        .at("/0/cvDocumentReference/selectedAt")
                        .asText());
        assertEquals(
                "44444444-4444-4444-8444-444444444444",
                objectMapper.valueToTree(response.getBody())
                        .at("/0/cvDocumentReference/evidenceProvenance/evidenceSnapshotId")
                        .asText());
        assertEquals(
                "QUALIFICATION_TRAINING",
                objectMapper.valueToTree(response.getBody())
                        .at("/0/cvDocumentReference/evidenceProvenance/evidenceRevisions/0/category")
                        .asText());
        assertEquals(
                "PROJECT",
                objectMapper.valueToTree(response.getBody())
                        .at("/0/cvDocumentReference/evidenceProvenance/evidenceRevisions/1/category")
                        .asText());
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
        assertEquals(
                "JOB_FINDER_APPLICATION_NOT_FOUND",
                crossUser.getBody().get("code"));
        assertTrue(DOWNSTREAM.applicationCalls().isEmpty());
    }

    @Test
    void sessionDerivedApplicationCreateAndListNeverAcceptBrowserOwnership() throws Exception {
        String token = JWKS.validToken("alice");
        HttpHeaders headers = authenticated(token);
        headers.set("X-User-Id", "victim");

        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/jobs/applications",
                HttpMethod.POST,
                new HttpEntity<>("""
                        {
                          "userId": "victim",
                          "jobId": "canonical-1",
                          "canonicalJobId": "canonical-1",
                          "provider": "REED",
                          "externalJobId": "reed-1",
                          "jobTitle": "Platform Engineer",
                          "companyName": "Example Ltd",
                          "location": "London"
                        }
                        """, headers),
                Map.class);
        ResponseEntity<List> listed = restTemplate.exchange(
                "/api/jobs/applications",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("alice", created.getBody().get("userId"));
        assertEquals(HttpStatus.OK, listed.getStatusCode());
        assertEquals("alice", ((Map<?, ?>) listed.getBody().get(0)).get("userId"));

        DownstreamCall createCall = DOWNSTREAM.applicationCalls().stream()
                .filter(call -> call.method().equals("POST")
                        && call.path().equals("/api/v1/applications"))
                .findFirst()
                .orElseThrow();
        JsonNode forwarded = objectMapper.readTree(createCall.body());
        assertEquals("alice", forwarded.get("userId").asText());
        assertEquals("MANUAL", forwarded.get("provenance").asText());
        assertEquals("SAVED", forwarded.get("initialStatus").asText());
        assertNull(createCall.userId());
        assertTrue(DOWNSTREAM.applicationCalls().stream().allMatch(call ->
                call.authorization().equals("Bearer " + token)));
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
        assertEquals(foreign.getBody().get("code"), unknown.getBody().get("code"));
        assertEquals(foreign.getBody().get("message"), unknown.getBody().get("message"));
        assertEquals(
                "JOB_FINDER_APPLICATION_NOT_FOUND",
                foreign.getBody().get("code"));
        assertFalse(DOWNSTREAM.applicationCalls().stream().anyMatch(call ->
                call.method().equals("PATCH")));
        assertTrue(DOWNSTREAM.applicationCalls().stream().allMatch(call ->
                call.authorization().equals("Bearer " + token)));
    }

    @Test
    void ownedStatusAndWithdrawForwardBearerWithoutDuplicatingTrackerCleanup() {
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
        assertEquals(0, DOWNSTREAM.documentCalls().size());
        assertTrue(DOWNSTREAM.applicationCalls().stream().allMatch(call ->
                call.authorization().equals("Bearer " + token)));
    }

    @Test
    void recoverableWithdrawalPreservesTrackerAcceptedOutcome() {
        String token = JWKS.validToken("alice");

        ResponseEntity<Map> withdraw = restTemplate.exchange(
                "/api/jobs/applications/" + PENDING_WITHDRAWAL_APPLICATION
                        + "/withdraw-generated",
                HttpMethod.POST,
                new HttpEntity<>(authenticated(token)),
                Map.class);

        assertEquals(HttpStatus.ACCEPTED, withdraw.getStatusCode());
        assertEquals(Boolean.FALSE, withdraw.getBody().get("withdrawn"));
        assertEquals("RECOVERY_REQUIRED", withdraw.getBody().get("operationStatus"));
        assertEquals(Boolean.TRUE, withdraw.getBody().get("retryable"));
        assertEquals("DOCUMENT_STORE_UNAVAILABLE", withdraw.getBody().get("recoveryCode"));
        assertEquals(0, DOWNSTREAM.documentCalls().size());
    }

    private ResponseEntity<Map> search(HttpHeaders headers, String body) {
        return restTemplate.exchange(
                "/api/jobs/search",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    private static void assertPayloadTooLarge(
            HttpStatusCode status,
            Map body,
            String correlationId) {
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, status);
        assertEquals("1", body.get("schemaVersion"));
        assertEquals("JOB_SEARCH_PAYLOAD_TOO_LARGE", body.get("code"));
        assertEquals(correlationId, body.get("correlationId"));
        assertFalse(body.toString().contains("Exception"));
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

    private static String canonicalJob() {
        return """
                {
                  "canonicalSchemaVersion": "2.0",
                  "canonicalJobId": "canonical-1",
                  "primarySource": "REED",
                  "externalJobId": "reed-1",
                  "title": "Platform Engineer",
                  "companyName": "Example Ltd",
                  "location": "London",
                  "description": "Build reliable services.",
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
                }
                """;
    }

    private static final class TestDownstreamServer implements AutoCloseable {

        private final AtomicReference<String> profileAuthorization = new AtomicReference<>();
        private final AtomicReference<String> profileCorrelationId = new AtomicReference<>();
        private final AtomicReference<String> jobAuthorization = new AtomicReference<>();
        private final AtomicReference<String> jobCorrelationId = new AtomicReference<>();
        private final AtomicReference<String> jobUserId = new AtomicReference<>();
        private final AtomicReference<String> jobBody = new AtomicReference<>();
        private final AtomicLong profileDelayMs = new AtomicLong();
        private final AtomicBoolean malformedJobResponse = new AtomicBoolean();
        private final List<DownstreamCall> applicationCalls = new CopyOnWriteArrayList<>();
        private final List<DownstreamCall> documentCalls = new CopyOnWriteArrayList<>();
        private final List<DownstreamCall> savedJobCalls = new CopyOnWriteArrayList<>();
        private final AtomicInteger savedJobCreates = new AtomicInteger();
        private HttpServer server;

        synchronized String baseUrl() {
            if (server == null) {
                try {
                    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                    server.createContext("/api/profiles/me", this::profile);
                    server.createContext("/api/jobs/search", this::jobs);
                    server.createContext(
                            "/api/jobs/REED/reed-1",
                            this::jobDetails);
                    server.createContext("/api/jobs/saved", this::savedJobs);
                    server.createContext("/api/v1/applications", this::applications);
                    server.createContext("/api/v1/documents", this::documents);
                    server.setExecutor(Executors.newCachedThreadPool());
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
            profileCorrelationId.set(
                    exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            delay(profileDelayMs.get());
            respond(exchange, 200, "{}");
        }

        private void jobs(HttpExchange exchange) throws IOException {
            jobAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            jobCorrelationId.set(
                    exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            jobUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            jobBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            if (malformedJobResponse.get()) {
                respond(exchange, 200, "{\"jobs\":");
                return;
            }
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
                      "resultsByTargetRole": [{
                        "targetRole": "Platform Engineer",
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
                        "totalResults": 37,
                        "page": 2,
                        "pageSize": 20,
                        "totalPages": 2,
                        "providerResults": [{
                          "provider": "REED",
                          "status": "SUCCESS",
                          "rawResultCount": 20
                        }, {
                          "provider": "ADZUNA",
                          "status": "TIMED_OUT",
                          "rawResultCount": 0,
                          "errorMessage": "Provider request timed out"
                        }],
                        "searchStatus": "PARTIAL",
                        "matchingStatus": "COMPLETE"
                      }],
                      "totalResults": 1,
                      "page": 2,
                      "pageSize": 20,
                      "totalPages": 5,
                      "sort": "NEWEST_POSTED",
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

        private void jobDetails(HttpExchange exchange) throws IOException {
            jobAuthorization.set(
                    exchange.getRequestHeaders().getFirst("Authorization"));
            jobCorrelationId.set(
                    exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            jobUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            respond(exchange, 200, """
                    {
                      "canonicalSchemaVersion": "2.0",
                      "canonicalJobId": "reed-1",
                      "primarySource": "REED",
                      "externalJobId": "reed-1",
                      "title": "Platform Engineer",
                      "description": "Complete provider job description.",
                      "descriptionCompleteness": "FULL",
                      "sources": [],
                      "fieldProvenance": []
                    }
                    """);
        }

        private void applications(HttpExchange exchange) throws IOException {
            DownstreamCall call = capture(exchange);
            applicationCalls.add(call);
            String path = call.path();

            if (call.method().equals("POST")
                    && path.equals("/api/v1/applications")) {
                JsonNode request = new ObjectMapper().readTree(call.body());
                respond(exchange, 201, application(
                        OWNED_APPLICATION,
                        request.get("userId").asText(),
                        request.get("initialStatus").asText()));
                return;
            }

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
                    : path.contains(PENDING_WITHDRAWAL_APPLICATION.toString())
                            ? PENDING_WITHDRAWAL_APPLICATION
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
                if (id.equals(PENDING_WITHDRAWAL_APPLICATION)) {
                    respond(exchange, 202, """
                            {
                              "applicationId": "%s",
                              "status": "NEW",
                              "withdrawn": false,
                              "operationId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                              "operationStatus": "RECOVERY_REQUIRED",
                              "retryable": true,
                              "recoveryCode": "DOCUMENT_STORE_UNAVAILABLE",
                              "message": "Generated application withdrawal is pending recovery."
                            }
                            """.formatted(id));
                    return;
                }
                respond(exchange, 200, """
                        {
                          "applicationId": "%s",
                          "status": "NEW",
                          "withdrawn": true,
                          "operationId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                          "operationStatus": "COMPLETED",
                          "retryable": false,
                          "message": "Generated application withdrawn"
                        }
                        """.formatted(id));
                return;
            }
            respond(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
        }

        private void savedJobs(HttpExchange exchange) throws IOException {
            DownstreamCall call = capture(exchange);
            savedJobCalls.add(call);
            String path = call.path();

            if (path.equals("/api/jobs/saved") && call.method().equals("POST")) {
                boolean created = savedJobCreates.incrementAndGet() == 1;
                exchange.getResponseHeaders().set(
                        "X-Saved-Job-Outcome",
                        created ? "CREATED" : "REPLAYED");
                respond(exchange, created ? 201 : 200, savedJob());
                return;
            }
            if (path.equals("/api/jobs/saved") && call.method().equals("GET")) {
                respond(exchange, 200, """
                        {
                          "items": [%s],
                          "page": 0,
                          "size": 20,
                          "totalElements": 1,
                          "totalPages": 1
                        }
                        """.formatted(savedJob()));
                return;
            }
            if (path.endsWith(UNAVAILABLE_SAVED_JOB.toString())) {
                respond(exchange, 503, """
                        {
                          "error": "INTERNAL_FAILURE",
                          "message": "database-host refused the connection"
                        }
                        """);
                return;
            }
            if (path.endsWith(SAVED_JOB.toString())
                    && call.method().equals("GET")) {
                respond(exchange, 200, savedJob());
                return;
            }
            if (call.method().equals("DELETE")) {
                noContent(exchange);
                return;
            }
            respond(exchange, 404, """
                    {
                      "error": "SAVED_JOB_NOT_FOUND",
                      "message": "Saved job was not found."
                    }
                    """);
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
                    exchange.getRequestHeaders().getFirst("X-User-Id"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }

        private String application(UUID id, String owner, String status) {
            return """
                    {
                      "id": "%s",
                      "userId": "%s",
                      "jobId": "canonical-1",
                      "canonicalJobId": "canonical-1",
                      "provider": "REED",
                      "externalJobId": "reed-1",
                      "provenance": "GENERATED",
                      "jobTitle": "Platform Engineer",
                      "companyName": "Example Ltd",
                      "location": "London",
                      "cvDocumentId": "00000000-0000-0000-0000-000000000011",
                      "coverLetterDocumentId": "00000000-0000-0000-0000-000000000012",
                      "cvDocumentReference": {
                        "documentId": "00000000-0000-0000-0000-000000000011",
                        "documentFamilyId": "10000000-0000-4000-8000-000000000011",
                        "jobId": "canonical-1",
                        "documentType": "CV",
                        "version": 1,
                        "contentSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "sourceType": "UPLOADED",
                        "originalContentSha256": "abababababababababababababababababababababababababababababababab",
                        "selectedAt": "2026-07-29T03:01:00",
                        "groundingState": "AI_GENERATED_EVIDENCE_VALIDATED",
                        "evidenceProvenance": {
                          "profileRevisionId": "33333333-3333-4333-8333-333333333333",
                          "profileContentDigest": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                          "evidenceSnapshotId": "44444444-4444-4444-8444-444444444444",
                          "evidenceSnapshotDigest": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                          "evidenceRevisions": [{
                            "entryId": "55555555-5555-4555-8555-555555555555",
                            "revisionId": "66666666-6666-4666-8666-666666666666",
                            "revisionNumber": 2,
                            "category": "QUALIFICATION_TRAINING",
                            "contentDigest": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                          }, {
                            "entryId": "88888888-8888-4888-8888-888888888888",
                            "revisionId": "99999999-9999-4999-8999-999999999999",
                            "revisionNumber": 1,
                            "category": "PROJECT",
                            "contentDigest": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
                          }],
                          "sectionOrder": ["QUALIFICATION_TRAINING", "PROJECT"],
                          "claimLedger": {
                            "ledgerId": "77777777-7777-4777-8777-777777777777",
                            "ledgerSha256": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                            "policyVersion": "2.0.0",
                            "parserVersion": "3.0.0"
                          },
                          "generatedAt": "2026-07-29T03:00:00Z"
                        }
                      },
                      "applicationUsedCvState": "UNKNOWN",
                      "applicationUsedCoverLetterState": "UNKNOWN",
                      "status": "%s",
                      "createdAt": "2026-07-29T03:00:00",
                      "updatedAt": "2026-07-29T03:05:00",
                      "version": 2
                    }
                    """.formatted(id, owner, status);
        }

        private String savedJob() {
            return """
                    {
                      "savedJobId": "%s",
                      "canonicalJobId": "canonical-1",
                      "canonicalSchemaVersion": "2.0",
                      "snapshotVersion": 1,
                      "contentVersion": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "contentSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "capturedAt": "2026-07-27T08:00:00Z",
                      "sourceRetrievedAt": "2026-07-27T07:55:00Z",
                      "sourceState": "SNAPSHOT",
                      "savedAt": "2026-07-27T08:00:00Z",
                      "updatedAt": "2026-07-27T08:00:00Z",
                      "job": %s
                    }
                    """.formatted(SAVED_JOB, canonicalJob());
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

        private static void noContent(HttpExchange exchange) throws IOException {
            exchange.getRequestBody().close();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }

        private static void delay(long milliseconds) throws IOException {
            if (milliseconds < 1) {
                return;
            }
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Downstream test delay interrupted", exception);
            }
        }

        void reset() {
            profileAuthorization.set(null);
            profileCorrelationId.set(null);
            jobAuthorization.set(null);
            jobCorrelationId.set(null);
            jobUserId.set(null);
            jobBody.set(null);
            applicationCalls.clear();
            documentCalls.clear();
            savedJobCalls.clear();
            savedJobCreates.set(0);
            profileDelayMs.set(0);
            malformedJobResponse.set(false);
        }

        String profileAuthorization() {
            return profileAuthorization.get();
        }

        String profileCorrelationId() {
            return profileCorrelationId.get();
        }

        String jobAuthorization() {
            return jobAuthorization.get();
        }

        String jobCorrelationId() {
            return jobCorrelationId.get();
        }

        void setProfileDelayMs(long milliseconds) {
            profileDelayMs.set(milliseconds);
        }

        void setMalformedJobResponse(boolean malformed) {
            malformedJobResponse.set(malformed);
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

        List<DownstreamCall> savedJobCalls() {
            return List.copyOf(savedJobCalls);
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
            String userId,
            String body) {
    }
}
