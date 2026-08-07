package com.jobseekercopilot.jobfindergateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.math.BigDecimal;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info()
                        .title("Jobseeker Copilot - Job Finder Gateway API")
                        .description("Gateway API for job search orchestration. Coordinates between user profile, job, and location services.")
                        .version("1.7.0"));
    }

    @Bean
    public OpenApiCustomizer targetRolePagingContractCustomizer() {
        return openApi -> {
            Schema<?> targetRoleResults = (Schema<?>) openApi.getComponents()
                    .getSchemas()
                    .get("TargetRoleJobResults");
            if (targetRoleResults == null) {
                return;
            }

            targetRoleResults.setRequired(List.of(
                    "targetRole",
                    "jobs",
                    "totalResults",
                    "page",
                    "pageSize",
                    "totalPages",
                    "providerResults",
                    "searchStatus",
                    "matchingStatus"));
            constrainInteger(targetRoleResults, "totalResults", 0, null);
            constrainInteger(targetRoleResults, "page", 1, 100);
            constrainInteger(targetRoleResults, "pageSize", 1, 50);
            constrainInteger(targetRoleResults, "totalPages", 0, null);
        };
    }

    private static void constrainInteger(
            Schema<?> parent,
            String propertyName,
            Integer minimum,
            Integer maximum) {
        Schema<?> property = (Schema<?>) parent.getProperties().get(propertyName);
        if (minimum != null) {
            property.setMinimum(BigDecimal.valueOf(minimum));
        }
        if (maximum != null) {
            property.setMaximum(BigDecimal.valueOf(maximum));
        }
    }
}
