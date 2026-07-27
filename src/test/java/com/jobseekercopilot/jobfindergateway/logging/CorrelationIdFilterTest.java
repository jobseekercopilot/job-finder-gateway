package com.jobseekercopilot.jobfindergateway.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CorrelationIdFilterTest {

    @Test
    void redactsApplicationOwnerAndResourceIdentifiersFromRequestPaths() {
        assertEquals(
                "/api/jobs/applications/user/{subject}",
                CorrelationIdFilter.redactedPath("/api/jobs/applications/user/alice@example.com"));
        assertEquals(
                "/api/jobs/applications/{applicationId}/status",
                CorrelationIdFilter.redactedPath(
                        "/api/jobs/applications/00000000-0000-0000-0000-000000000001/status"));
        assertEquals(
                "/api/jobs/saved/{savedJobId}",
                CorrelationIdFilter.redactedPath(
                        "/api/jobs/saved/10000000-0000-4000-8000-000000000001"));
        assertEquals(
                "/api/jobs/search",
                CorrelationIdFilter.redactedPath("/api/jobs/search"));
    }

    @Test
    void preservesOnlyBoundedLogAndHeaderSafeCorrelationIds() {
        assertEquals(
                "trace_123.example-test",
                CorrelationIdFilter.safeCorrelationId("trace_123.example-test"));

        for (String unsafe : new String[]{
                "",
                "contains a space",
                "line\r\nbreak",
                "slash/value",
                "a".repeat(129)}) {
            String replacement = CorrelationIdFilter.safeCorrelationId(unsafe);
            assertNotEquals(unsafe, replacement);
            assertTrue(replacement.matches("[a-f0-9-]{36}"));
        }
    }
}
