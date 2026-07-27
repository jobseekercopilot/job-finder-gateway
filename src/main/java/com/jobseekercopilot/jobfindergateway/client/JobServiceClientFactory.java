package com.jobseekercopilot.jobfindergateway.client;

import com.jobseekercopilot.generated.jobservice.api.JobSearchApi;
import com.jobseekercopilot.generated.jobservice.api.SavedJobsApi;
import com.jobseekercopilot.generated.jobservice.client.ApiClient;
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
        return new JobSearchApi(authenticatedClient(accessToken));
    }

    public SavedJobsApi authenticatedSavedJobs(String accessToken) {
        return new SavedJobsApi(authenticatedClient(accessToken));
    }

    private ApiClient authenticatedClient(String accessToken) {
        var apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        apiClient.setBearerToken(accessToken);
        return apiClient;
    }
}
