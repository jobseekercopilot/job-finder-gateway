package com.jobseekercopilot.jobfindergateway.client;

import com.jobseekercopilot.generated.userprofileservice.api.EvidenceLibraryApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import com.jobseekercopilot.generated.userprofileservice.client.ApiClient;
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
        return new UserProfilesApi(authenticatedClient(accessToken));
    }

    public EvidenceLibraryApi authenticatedEvidence(String accessToken) {
        return new EvidenceLibraryApi(authenticatedClient(accessToken));
    }

    private ApiClient authenticatedClient(String accessToken) {
        var apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        apiClient.setBearerToken(accessToken);
        return apiClient;
    }
}
