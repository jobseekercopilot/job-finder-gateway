package com.jobseekercopilot.jobfindergateway.controller;

import com.jobseekercopilot.jobfindergateway.client.JobServiceClient;
import com.jobseekercopilot.jobfindergateway.client.UserProfileClient;
import com.jobseekercopilot.jobfindergateway.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobfindergateway.model.dto.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    private static final String USER_ID_ATTRIBUTE = "USER_ID";

    private final UserProfileClient userProfileClient;
    private final JobServiceClient jobServiceClient;

    public JobSearchController(UserProfileClient userProfileClient,
                               JobServiceClient jobServiceClient) {
        this.userProfileClient = userProfileClient;
        this.jobServiceClient = jobServiceClient;
    }

    @PostMapping("/search")
    public ResponseEntity<String> searchJobs(HttpServletRequest request,
                                              @RequestBody(required = false) JobSearchRequest searchRequest) {
        String userId = (String) request.getAttribute(USER_ID_ATTRIBUTE);

        if (userId == null || userId.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid user ID\"}");
        }

        // If no search request body provided, fall back to profile-based search
        if (searchRequest == null) {
            UserProfile userProfile;
            try {
                userProfile = userProfileClient.fetchUserProfile(userId);
            } catch (UserProfileClient.ServiceUnavailableException e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"User profile service is currently unavailable\"}");
            }

            if (userProfile == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"PROFILE_NOT_FOUND\",\"message\":\"User profile does not exist\"}");
            }

            if (userProfile.getDesiredRoles() == null || userProfile.getDesiredRoles().isEmpty() ||
                userProfile.getEmploymentType() == null || userProfile.getEmploymentType().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"INVALID_REQUEST\",\"message\":\"User profile is incomplete. Please update your aspirations and work preferences.\"}");
            }

            try {
                return jobServiceClient.searchJobsFromProfile(userId, userProfile);
            } catch (JobServiceClient.ServiceUnavailableException e) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"Job search service is currently unavailable\"}");
            }
        }

        // Use the request body from the frontend (preferred flow)
        try {
            return jobServiceClient.searchJobs(userId, searchRequest);
        } catch (JobServiceClient.ServiceUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"Job search service is currently unavailable\"}");
        }
    }
}