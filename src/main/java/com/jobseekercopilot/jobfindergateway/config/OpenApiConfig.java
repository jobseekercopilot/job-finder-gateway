package com.jobseekercopilot.jobfindergateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Jobseeker Copilot - Job Finder Gateway API")
                        .description("Gateway API for job search orchestration. Coordinates between user profile, job, and location services.")
                        .version("1.0.0"));
    }
}