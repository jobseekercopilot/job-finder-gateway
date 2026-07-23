package com.jobseekercopilot.jobfindergateway.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                "/api/jobs/search",
                CorrelationIdFilter.redactedPath("/api/jobs/search"));
    }
}
