package com.jobseekercopilot.jobfindergateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.generated.jobservice.model.ReedJobSearchResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class JobServiceRoleResultsSerializationTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void generatedBoundaryPreservesRoleSpecificPagingAndDependencyOutcomes() throws Exception {
        ReedJobSearchResponse response = objectMapper.readValue(
                """
                {
                  "jobs": [],
                  "resultsByTargetRole": [{
                    "targetRole": "Platform Engineer",
                    "jobs": [],
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
                  "totalResults": 20,
                  "page": 2,
                  "pageSize": 20,
                  "totalPages": 1,
                  "sort": "NEWEST_POSTED",
                  "providerResults": [],
                  "searchStatus": "PARTIAL",
                  "matchingStatus": "COMPLETE"
                }
                """,
                ReedJobSearchResponse.class);

        JsonNode serialized = objectMapper.valueToTree(response);
        JsonNode roleResult = serialized.at("/resultsByTargetRole/0");
        assertEquals("Platform Engineer", roleResult.at("/targetRole").asText());
        assertEquals(37, roleResult.at("/totalResults").asInt());
        assertEquals(2, roleResult.at("/page").asInt());
        assertEquals(20, roleResult.at("/pageSize").asInt());
        assertEquals(2, roleResult.at("/totalPages").asInt());
        assertEquals("SUCCESS", roleResult.at("/providerResults/0/status").asText());
        assertEquals("TIMED_OUT", roleResult.at("/providerResults/1/status").asText());
        assertEquals("PARTIAL", roleResult.at("/searchStatus").asText());
        assertEquals("COMPLETE", roleResult.at("/matchingStatus").asText());
    }

    @Test
    void generatedBoundaryAcceptsRetainedApplicationUtcTimestamps()
            throws Exception {
        ReedJobSearchResponse response = objectMapper.readValue(
                """
                {
                  "jobs": [{
                    "canonicalJobId": "canonical-1",
                    "applicationStatus": "SAVED",
                    "applicationId": "1d4fe4de-dc83-4bdc-b66e-951f54c2e5a8",
                    "applicationUpdatedAt": "2026-07-29T22:23:45.703864Z"
                  }, {
                    "canonicalJobId": "canonical-2",
                    "applicationStatus": "APPLIED",
                    "applicationId": "22df2092-cdf6-4d51-a758-173a7eb9bd62",
                    "appliedAt": "2026-07-29T21:23:45.703864Z",
                    "applicationUpdatedAt": "2026-07-29T22:23:45.703864Z"
                  }],
                  "resultsByTargetRole": [],
                  "providerResults": []
                }
                """,
                ReedJobSearchResponse.class);

        var retainedJob = response.getJobs().get(0);
        assertEquals("SAVED", retainedJob.getApplicationStatus());
        assertEquals(
                OffsetDateTime.parse("2026-07-29T22:23:45.703864Z"),
                retainedJob.getApplicationUpdatedAt());
        var appliedJob = response.getJobs().get(1);
        assertEquals("APPLIED", appliedJob.getApplicationStatus());
        assertEquals(
                OffsetDateTime.parse("2026-07-29T21:23:45.703864Z"),
                appliedJob.getAppliedAt());
    }
}
