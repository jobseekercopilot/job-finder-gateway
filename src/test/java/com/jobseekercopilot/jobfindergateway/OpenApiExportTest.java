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
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"), spec);
        org.junit.jupiter.api.Assertions.assertEquals(
                objectMapper.readTree(Files.readString(Path.of("api/openapi.json"))),
                contract,
                "Published gateway OpenAPI contract is stale; copy target/openapi.json after review");
    }
}
