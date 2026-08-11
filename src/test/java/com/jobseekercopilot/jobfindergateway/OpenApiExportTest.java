package com.jobseekercopilot.jobfindergateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    void exportOpenApi() throws Exception {
        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var contract = objectMapper.readTree(spec);
        org.junit.jupiter.api.Assertions.assertEquals(
                "bearer",
                contract.at("/components/securitySchemes/bearerAuth/scheme").asText());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at("/security/0/bearerAuth").isArray());
        org.junit.jupiter.api.Assertions.assertEquals(
                "1.12.0",
                contract.at("/info/version").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                contract.at(
                        "/components/schemas/JobSearchRequest/properties/page/minimum")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                100,
                contract.at(
                        "/components/schemas/JobSearchRequest/properties/page/maximum")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                contract.at(
                        "/components/schemas/JobSearchRequest/properties/pageSize/minimum")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                50,
                contract.at(
                        "/components/schemas/JobSearchRequest/properties/pageSize/maximum")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                "JOB_TITLE_AZ",
                contract.at(
                        "/components/schemas/JobSearchRequest/properties/sort/enum/6")
                        .asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "aspirations",
                contract.at("/components/schemas/JobSearchRequest/required/0").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                5,
                contract.at(
                        "/components/schemas/JobSearchRequest/properties/selectedProviders/maxItems")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                contract.at(
                        "/components/schemas/Aspirations/properties/desiredRoles/minItems")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                10,
                contract.at(
                        "/components/schemas/Aspirations/properties/desiredRoles/maxItems")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                120,
                contract.at(
                        "/components/schemas/Aspirations/properties/desiredRoles/items/maxLength")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                10,
                contract.at(
                        "/components/schemas/Aspirations/properties/locations/maxItems")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                contract.at(
                        "/components/schemas/SalaryExpectation/properties/min/minimum")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                "#/components/schemas/ApiErrorResponse",
                contract.at(
                        "/paths/~1api~1jobs~1search/post/responses/400/content/application~1json/schema/$ref")
                        .asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "#/components/schemas/ApiErrorResponse",
                contract.at(
                        "/paths/~1api~1jobs~1search/post/responses/413/content/application~1json/schema/$ref")
                        .asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                4,
                contract.at("/components/schemas/ApiErrorResponse/required").size());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at(
                        "/components/schemas/ApiErrorResponse/properties/correlationId")
                        .isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at(
                        "/components/schemas/ReedJobSearchResponse/properties/totalPages")
                        .isObject());
        org.junit.jupiter.api.Assertions.assertEquals(
                "MOST_RELEVANT",
                contract.at(
                        "/components/schemas/ReedJobSearchResponse/properties/sort/enum/0")
                        .asText());
        for (String roleResultField : java.util.List.of(
                "targetRole",
                "jobs",
                "totalResults",
                "page",
                "pageSize",
                "totalPages",
                "providerResults",
                "searchStatus",
                "matchingStatus")) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    contract.at(
                            "/components/schemas/TargetRoleJobResults/properties/"
                                    + roleResultField)
                            .isObject(),
                    "Missing role result field " + roleResultField);
        }
        org.junit.jupiter.api.Assertions.assertEquals(
                9,
                contract.at("/components/schemas/TargetRoleJobResults/required").size());
        org.junit.jupiter.api.Assertions.assertEquals(
                "UNAVAILABLE",
                contract.at(
                        "/components/schemas/TargetRoleJobResults/properties/searchStatus/enum/2")
                        .asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                100,
                contract.at(
                        "/components/schemas/TargetRoleJobResults/properties/page/maximum")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                50,
                contract.at(
                        "/components/schemas/TargetRoleJobResults/properties/pageSize/maximum")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at("/paths/~1api~1jobs~1saved/post").isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at("/paths/~1api~1jobs~1saved/get").isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at("/paths/~1api~1jobs~1saved~1{savedJobId}/get").isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at("/paths/~1api~1jobs~1saved~1{savedJobId}/delete").isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at("/paths/~1api~1jobs~1applications/post").isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at("/paths/~1api~1jobs~1applications/get").isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at(
                        "/components/schemas/CreateTrackedApplicationRequest/properties/userId")
                        .isMissingNode());
        for (String sourceField : java.util.List.of(
                "listingUrl",
                "applyUrl",
                "attributionLabel",
                "attributionSourceUrl",
                "licenceUrl",
                "disclaimer")) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    contract.at(
                            "/components/schemas/CreateTrackedApplicationRequest/properties/"
                                    + sourceField)
                            .isObject(),
                    "Missing application source field " + sourceField);
        }
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at(
                        "/components/schemas/ApplicationRecordResponse/properties/applicationUsedCvDocumentReference")
                        .isObject());
        org.junit.jupiter.api.Assertions.assertEquals(
                "SELECTED",
                contract.at(
                        "/components/schemas/ApplicationRecordResponse/properties/applicationUsedCvState/enum/1")
                        .asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "OMITTED",
                contract.at(
                        "/components/schemas/ApplicationRecordResponse/properties/applicationUsedCoverLetterState/enum/2")
                        .asText());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at(
                        "/components/schemas/ApplicationRecordResponse/properties/canonicalJobId")
                        .isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at(
                        "/components/schemas/ApplicationRecordResponse/properties/version")
                        .isObject());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Idempotency-Key",
                contract.at(
                        "/paths/~1api~1jobs~1applications~1{applicationId}~1status/patch/parameters/1/name")
                        .asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                128,
                contract.at(
                        "/paths/~1api~1jobs~1applications~1{applicationId}~1status/patch/parameters/1/schema/maxLength")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                contract.at(
                        "/components/schemas/UpdateApplicationStatusRequest/properties/expectedVersion/minimum")
                        .asInt());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at(
                        "/components/schemas/DocumentVersionReference/properties/evidenceProvenance")
                        .isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at(
                        "/components/schemas/DocumentVersionReference/properties/sourceType")
                        .isObject());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at(
                        "/components/schemas/DocumentVersionReference/properties/originalContentSha256")
                        .isObject());
        org.junit.jupiter.api.Assertions.assertEquals(
                "date-time",
                contract.at(
                        "/components/schemas/DocumentVersionReference/properties/selectedAt/format")
                        .asText());
        org.junit.jupiter.api.Assertions.assertTrue(
                contract.at("/components/schemas/SavedJobResponse/properties/savedJobId")
                        .isObject());
        org.junit.jupiter.api.Assertions.assertEquals(
                "CREATED",
                contract.at(
                        "/paths/~1api~1jobs~1saved/post/responses/201/headers/X-Saved-Job-Outcome/schema/enum/0")
                        .asText());
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"), spec);
        org.junit.jupiter.api.Assertions.assertEquals(
                objectMapper.readTree(Files.readString(Path.of("api/openapi.json"))),
                contract,
                "Published gateway OpenAPI contract is stale; copy target/openapi.json after review");
    }
}
