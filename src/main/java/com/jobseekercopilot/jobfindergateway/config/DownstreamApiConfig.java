package com.jobseekercopilot.jobfindergateway.config;

import com.jobseekercopilot.generated.jobservice.api.JobSearchApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DownstreamApiConfig {

    @Bean
    JobSearchApi jobServiceApi(
            @Value("${services.job-service.url}") String basePath) {
        var apiClient = new com.jobseekercopilot.generated.jobservice.client.ApiClient();
        apiClient.setBasePath(basePath);
        return new JobSearchApi(apiClient);
    }

    @Bean
    UserProfilesApi userProfileServiceApi(
            @Value("${services.user-profile.url}") String basePath) {
        var apiClient = new com.jobseekercopilot.generated.userprofileservice.client.ApiClient();
        apiClient.setBasePath(basePath);
        return new UserProfilesApi(apiClient);
    }

}
