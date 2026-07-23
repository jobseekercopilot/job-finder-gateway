package com.jobseekercopilot.jobfindergateway.config;

import com.jobseekercopilot.generated.jobservice.api.JobSearchApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DownstreamApiConfig {

    @Bean
    JobSearchApi jobServiceApi(
            @Value("${services.job-service.url}") String basePath,
            RestTemplate restTemplate) {
        var apiClient = new com.jobseekercopilot.generated.jobservice.client.ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        return new JobSearchApi(apiClient);
    }
}
