package com.jobseekercopilot.jobfindergateway.client;

import com.jobseekercopilot.generated.jobservice.api.JobSearchApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class JobServiceClientFactory {

    private final RestTemplate restTemplate;
    private final String basePath;

    public JobServiceClientFactory(
            RestTemplate restTemplate,
            @Value("${services.job-service.url}") String basePath) {
        this.restTemplate = restTemplate;
        this.basePath = basePath;
    }

    public JobSearchApi authenticated(String accessToken) {
        var apiClient =
                new com.jobseekercopilot.generated.jobservice.client.ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        apiClient.setBearerToken(accessToken);
        return new JobSearchApi(apiClient);
    }
}
