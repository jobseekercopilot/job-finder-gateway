package com.jobseekercopilot.jobfindergateway.client;

import com.jobseekercopilot.jobfindergateway.model.dto.UserProfile;
import com.jobseekercopilot.jobfindergateway.util.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserProfileClient {

    private final RestTemplate restTemplate;
    private final String userProfileServiceUrl;
    private final JsonParser jsonParser;

    private static final String X_USER_ID_HEADER = "X-User-Id";

    public UserProfileClient(RestTemplate restTemplate,
                             JsonParser jsonParser,
                             @Value("${services.user-profile.url}") String userProfileServiceUrl) {
        this.restTemplate = restTemplate;
        this.userProfileServiceUrl = userProfileServiceUrl;
        this.jsonParser = jsonParser;
    }

    public UserProfile fetchUserProfile(String userId) {
        String url = userProfileServiceUrl + "/api/profiles/me";

        HttpHeaders headers = new HttpHeaders();
        headers.set(X_USER_ID_HEADER, userId);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<UserProfile> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserProfile.class
            );

            UserProfile profile = response.getBody();
            
            // Parse JSON strings into flattened fields
            if (profile != null) {
                jsonParser.parseAspirationsAndWorkPrefs(profile);
            }
            
            return profile;
        } catch (Exception e) {
            throw new ServiceUnavailableException("User profile service is currently unavailable", e);
        }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}