package com.jobseekercopilot.jobfindergateway.client;

import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserProfileClientFactory {

    private final RestTemplate restTemplate;
    private final String basePath;

    public UserProfileClientFactory(
            RestTemplate restTemplate,
            @Value("${services.user-profile.url}") String basePath) {
        this.restTemplate = restTemplate;
        this.basePath = basePath;
    }

    public UserProfilesApi authenticated(String accessToken) {
        var apiClient =
                new com.jobseekercopilot.generated.userprofileservice.client.ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        apiClient.setBearerToken(accessToken);
        return new UserProfilesApi(apiClient);
    }
}
