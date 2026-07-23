package com.jobseekercopilot.jobfindergateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import com.jobseekercopilot.jobfindergateway.model.dto.ApplicationRecordResponse;
import com.jobseekercopilot.jobfindergateway.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobfindergateway.model.dto.JobSearchResponse;
import com.jobseekercopilot.jobfindergateway.model.dto.UpdateApplicationStatusRequest;
import com.jobseekercopilot.jobfindergateway.model.dto.WithdrawGeneratedApplicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Job Search", description = "Job search orchestration endpoints")
public class JobSearchController {

    private static final Logger log = LoggerFactory.getLogger(JobSearchController.class);
    private static final String USER_ID_ATTRIBUTE = "USER_ID";
    private static final String X_USER_ID_HEADER = "X-User-Id";

    private final UserProfilesApi userProfilesApi;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String jobServiceBaseUrl;
    private final String applicationTrackerBaseUrl;
    private final String documentStoreBaseUrl;

    public JobSearchController(UserProfilesApi userProfilesApi,
                               ObjectMapper objectMapper,
                               RestTemplate restTemplate,
                               @Value("${services.job-service.url}") String jobServiceBaseUrl,
                               @Value("${services.application-tracker.url}") String applicationTrackerBaseUrl,
                               @Value("${services.document-store.url:http://document-store-service:8089}") String documentStoreBaseUrl) {
        this.userProfilesApi = userProfilesApi;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.jobServiceBaseUrl = jobServiceBaseUrl;
        this.applicationTrackerBaseUrl = applicationTrackerBaseUrl;
        this.documentStoreBaseUrl = documentStoreBaseUrl;
    }

    @PostMapping("/search")
    @Operation(summary = "Search jobs", description = "Searches for jobs using either a provided search request or the user's profile preferences.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or incomplete user profile",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Missing or invalid user ID",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Downstream service unavailable",
                    content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Search")
    public ResponseEntity<?> searchJobs(HttpServletRequest request,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", description = "User ID for authentication (optional if JWT filter sets USER_ID attribute)", required = false, example = "user-123")
            @RequestHeader(name = X_USER_ID_HEADER, required = false) String xUserId,
            @Parameter(description = "Job search request parameters (optional - if omitted, uses user profile preferences)", required = false)
            @RequestBody(required = false) JobSearchRequest searchRequest) {
        long startedAt = System.nanoTime();
        // Try JWT-filter-set attribute first, then X-User-Id header (demo mode)
        String userId = (String) request.getAttribute(USER_ID_ATTRIBUTE);
        if (userId == null || userId.trim().isEmpty()) {
            userId = xUserId;
        }

        if (userId == null || userId.trim().isEmpty()) {
            log.warn("job-finder-gateway job search rejected reason=MissingUserId durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid user ID\"}");
        }
        log.info("job-finder-gateway received job search userId={} requestSource={}",
                userId,
                searchRequest == null ? "PROFILE" : "REQUEST_BODY");

        // If no search request body provided, fall back to profile-based search
        if (searchRequest == null) {
            com.jobseekercopilot.generated.userprofileservice.model.UserProfile userProfile;
            try {
                long profileStartedAt = System.nanoTime();
                log.info("Calling user-profile-service for job search userId={}", userId);
                userProfile = userProfilesApi.getMyProfile(userId);
                log.info("user-profile-service returned profile for job search userId={} durationMs={}",
                        userId,
                        (System.nanoTime() - profileStartedAt) / 1_000_000);
            } catch (RestClientException e) {
                log.warn("user-profile-service failed for job search userId={} durationMs={} error={}",
                        userId,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        e.getClass().getSimpleName(),
                        e);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"User profile service is currently unavailable\"}");
            }

            if (userProfile == null) {
                log.warn("job-finder-gateway job search rejected userId={} reason=ProfileNotFound durationMs={}",
                        userId,
                        (System.nanoTime() - startedAt) / 1_000_000);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"PROFILE_NOT_FOUND\",\"message\":\"User profile does not exist\"}");
            }

            if (desiredRoles(userProfile).isEmpty() || locations(userProfile).isEmpty()) {
                log.warn("job-finder-gateway job search rejected userId={} reason=IncompleteProfile roles={} locations={} durationMs={}",
                        userId,
                        desiredRoles(userProfile).size(),
                        locations(userProfile).size(),
                        (System.nanoTime() - startedAt) / 1_000_000);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"INVALID_REQUEST\",\"message\":\"User profile is incomplete. Please update your aspirations and work preferences.\"}");
            }

            try {
                ResponseEntity<JobSearchResponse> response = searchDownstream(userId, fromProfile(userProfile));
                log.info("job-finder-gateway job search completed userId={} finalCount={} durationMs={}",
                        userId,
                        response.getBody() == null ? null : response.getBody().getTotalResults(),
                        (System.nanoTime() - startedAt) / 1_000_000);
                return response;
            } catch (RestClientException e) {
                log.warn("job-service failed for profile job search userId={} durationMs={} error={}",
                        userId,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        e.getClass().getSimpleName(),
                        e);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"Job search service is currently unavailable\"}");
            }
        }

        // Use the request body from the frontend (preferred flow)
        try {
            ResponseEntity<JobSearchResponse> response = searchDownstream(userId, searchRequest);
            log.info("job-finder-gateway job search completed userId={} finalCount={} durationMs={}",
                    userId,
                    response.getBody() == null ? null : response.getBody().getTotalResults(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return response;
        } catch (RestClientException e) {
            log.warn("job-service failed for job search userId={} durationMs={} error={}",
                    userId,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    e.getClass().getSimpleName(),
                    e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"Job search service is currently unavailable\"}");
        }
    }

    private ResponseEntity<JobSearchResponse> searchDownstream(
            String userId,
            Object request) {
        long startedAt = System.nanoTime();
        int roles = request instanceof JobSearchRequest jobSearchRequest
                && jobSearchRequest.getAspirations() != null
                && jobSearchRequest.getAspirations().getDesiredRoles() != null
                ? jobSearchRequest.getAspirations().getDesiredRoles().size()
                : 0;
        log.info("Calling job-service /api/jobs/search userId={} targetRoles={}", userId, roles);
        HttpHeaders headers = new HttpHeaders();
        headers.set(X_USER_ID_HEADER, userId);
        var response = restTemplate.postForObject(
                jobServiceBaseUrl + "/api/jobs/search",
                new HttpEntity<>(request, headers),
                Object.class);
        JobSearchResponse converted = objectMapper.convertValue(response, JobSearchResponse.class);
        log.info("job-service returned status=200 userId={} totalResults={} durationMs={}",
                userId,
                converted == null ? null : converted.getTotalResults(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return ResponseEntity.ok(converted);
    }

    @GetMapping("/applications/user/{userId}")
    @Operation(summary = "Get persisted applications for user", description = "Proxies saved application records from application-tracker-service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Applications returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApplicationRecordResponse.class))),
            @ApiResponse(responseCode = "503", description = "Application tracker unavailable", content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Applications")
    public ResponseEntity<?> getApplicationsForUser(
            @Parameter(description = "User ID to retrieve applications for") @PathVariable String userId) {
        long startedAt = System.nanoTime();
        try {
            log.info("Calling application-tracker-service application list userId={}", userId);
            ResponseEntity<List<ApplicationRecordResponse>> response = restTemplate.exchange(
                    applicationTrackerBaseUrl + "/api/v1/applications/user/" + userId,
                    org.springframework.http.HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<List<ApplicationRecordResponse>>() {});
            log.info("application-tracker-service list returned userId={} responseStatus={} count={} durationMs={}",
                    userId,
                    response.getStatusCode().value(),
                    response.getBody() == null ? 0 : response.getBody().size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (RestClientException e) {
            log.warn("Application tracker list failed for userId={} durationMs={} error={}",
                    userId,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    e.getClass().getSimpleName(),
                    e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"Application tracker service is currently unavailable\"}");
        }
    }

    @PatchMapping("/applications/{applicationId}/status")
    @Operation(summary = "Update job application status", description = "Proxies a job-card status update to application-tracker-service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApplicationRecordResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status request", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Missing or invalid user ID", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Application record not found", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Application tracker unavailable", content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Applications")
    public ResponseEntity<?> updateApplicationStatus(
            @Parameter(description = "Application tracker record ID") @PathVariable UUID applicationId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", description = "User ID for authentication (optional if JWT filter sets USER_ID attribute)", required = false, example = "user-123")
            @RequestHeader(name = X_USER_ID_HEADER, required = false) String xUserId,
            @Parameter(description = "Status update request") @jakarta.validation.Valid @RequestBody UpdateApplicationStatusRequest request) {
        long startedAt = System.nanoTime();
        String normalizedStatus = request.status().toUpperCase(Locale.ROOT);
        if (!supportedStatuses().contains(normalizedStatus)) {
            log.warn("job-finder-gateway application status rejected applicationId={} status={} durationMs={}",
                    applicationId,
                    normalizedStatus,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"INVALID_STATUS\",\"message\":\"Unsupported application status\"}");
        }

        try {
            log.info("Calling application-tracker-service status update applicationId={} status={}",
                    applicationId,
                    normalizedStatus);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Object> response = restTemplate.exchange(
                    applicationTrackerBaseUrl + "/api/v1/applications/" + applicationId + "/status",
                    org.springframework.http.HttpMethod.PATCH,
                    new HttpEntity<>(new UpdateApplicationStatusRequest(normalizedStatus), headers),
                    Object.class);
            log.info("application-tracker-service status update returned applicationId={} status={} responseStatus={} durationMs={}",
                    applicationId,
                    normalizedStatus,
                    response.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity
                    .status(response.getStatusCode())
                    .body(objectMapper.convertValue(response.getBody(), ApplicationRecordResponse.class));
        } catch (HttpStatusCodeException e) {
            log.warn("application-tracker-service status update rejected applicationId={} responseStatus={} durationMs={}",
                    applicationId,
                    e.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.warn("Application tracker status update failed for application {}", applicationId, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"Application tracker service is currently unavailable\"}");
        }
    }

    @PostMapping("/applications/{applicationId}/withdraw-generated")
    @Operation(summary = "Withdraw generated application", description = "Resets a generated-only application to NEW and removes generated document references.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Generated application withdrawn",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WithdrawGeneratedApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Application has already progressed and cannot be reset", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Missing or invalid user ID", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Application record not found", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Application tracker unavailable", content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Applications")
    public ResponseEntity<?> withdrawGeneratedApplication(
            @Parameter(description = "Application tracker record ID") @PathVariable UUID applicationId,
            @Parameter(in = ParameterIn.HEADER, name = "X-User-Id", description = "User ID for authentication (optional if JWT filter sets USER_ID attribute)", required = false, example = "user-123")
            @RequestHeader(name = X_USER_ID_HEADER, required = false) String xUserId) {
        long startedAt = System.nanoTime();
        log.info("job-finder-gateway generated application withdraw received applicationId={}", applicationId);
        ApplicationRecordResponse existingRecord = null;
        try {
            log.info("Calling application-tracker-service get application applicationId={}", applicationId);
            ResponseEntity<Object> existing = restTemplate.exchange(
                    applicationTrackerBaseUrl + "/api/v1/applications/" + applicationId,
                    org.springframework.http.HttpMethod.GET,
                    HttpEntity.EMPTY,
                    Object.class);
            existingRecord = objectMapper.convertValue(existing.getBody(), ApplicationRecordResponse.class);

            log.info("Calling application-tracker-service withdraw generated applicationId={}", applicationId);
            ResponseEntity<Object> response = restTemplate.exchange(
                    applicationTrackerBaseUrl + "/api/v1/applications/" + applicationId + "/withdraw-generated",
                    org.springframework.http.HttpMethod.POST,
                    HttpEntity.EMPTY,
                    Object.class);
            cleanupGeneratedDocuments(existingRecord);
            log.info("Generated application withdraw completed applicationId={} responseStatus={} durationMs={}",
                    applicationId,
                    response.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity
                    .status(response.getStatusCode())
                    .body(objectMapper.convertValue(response.getBody(), WithdrawGeneratedApplicationResponse.class));
        } catch (HttpStatusCodeException e) {
            log.warn("Generated application withdraw rejected applicationId={} responseStatus={} durationMs={}",
                    applicationId,
                    e.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity.status(e.getStatusCode())
                    .body(e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.warn("Generated application withdraw failed applicationId={} durationMs={} error={}",
                    applicationId,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    e.getClass().getSimpleName(),
                    e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"Application tracker service is currently unavailable\"}");
        }
    }

    private JobSearchRequest fromProfile(
            com.jobseekercopilot.generated.userprofileservice.model.UserProfile profile) {
        var aspirations = new JobSearchRequest.Aspirations();
        aspirations.setDesiredRoles(desiredRoles(profile));
        aspirations.setIndustries(List.of());
        aspirations.setLocations(locations(profile));

        var workPreferences = new JobSearchRequest.WorkPreferences();
        workPreferences.setEmploymentType(employmentTypes(profile));
        workPreferences.setCompanySize(List.of());
        workPreferences.setCulture(List.of());

        var sourceLocation = profile.getWorkPreferences().getLocation();
        workPreferences.setHomeLatitude(sourceLocation.getLatitude());
        workPreferences.setHomeLongitude(sourceLocation.getLongitude());

        var homeLocation = new JobSearchRequest.HomeLocation();
        homeLocation.setDisplayName(firstNonBlank(sourceLocation.getAdminDistrict(), sourceLocation.getRegion()));
        homeLocation.setPostcode(sourceLocation.getPostcode());
        homeLocation.setLatitude(sourceLocation.getLatitude());
        homeLocation.setLongitude(sourceLocation.getLongitude());

        var request = new JobSearchRequest();
        request.setAspirations(aspirations);
        request.setWorkPreferences(workPreferences);
        request.setHomeLocation(homeLocation);
        return request;
    }

    private List<String> desiredRoles(
            com.jobseekercopilot.generated.userprofileservice.model.UserProfile profile) {
        return profile.getAspirations() == null || profile.getAspirations().getTargetRoles() == null
                ? List.of()
                : profile.getAspirations().getTargetRoles();
    }

    private List<String> locations(
            com.jobseekercopilot.generated.userprofileservice.model.UserProfile profile) {
        if (profile.getWorkPreferences() == null
                || profile.getWorkPreferences().getLocation() == null) {
            return List.of();
        }
        var source = profile.getWorkPreferences().getLocation();
        List<String> locations = new ArrayList<>();
        addIfPresent(locations, source.getPostcode());
        addIfPresent(locations, source.getRegion());
        addIfPresent(locations, source.getAdminDistrict());
        return locations;
    }

    private List<String> employmentTypes(
            com.jobseekercopilot.generated.userprofileservice.model.UserProfile profile) {
        if (profile.getAspirations() == null
                || profile.getAspirations().getTargetWeeklyHours() == null) {
            return List.of();
        }
        return switch (profile.getAspirations().getTargetWeeklyHours()) {
            case FULL_TIME -> List.of("FULL_TIME");
            case PART_TIME_16_30, PART_TIME_UNDER_16 -> List.of("PART_TIME");
            case FLEXIBLE -> List.of();
        };
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private Set<String> supportedStatuses() {
        return Set.of(
                "DOCUMENTS_GENERATED",
                "APPLIED",
                "INTERVIEW",
                "UNSUCCESSFUL",
                "OFFER",
                "ACCEPTED",
                "REJECTED_BY_USER",
                "WITHDRAWN"
        );
    }

    private void cleanupGeneratedDocuments(ApplicationRecordResponse record) {
        if (record == null) {
            return;
        }
        deleteGeneratedDocument(record.cvDocumentId());
        deleteGeneratedDocument(record.coverLetterDocumentId());
    }

    private void deleteGeneratedDocument(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        try {
            log.info("Deleting generated document during withdraw documentId={}", documentId);
            restTemplate.delete(documentStoreBaseUrl + "/api/v1/documents/{id}", UUID.fromString(documentId));
            log.info("Generated document deleted during withdraw documentId={}", documentId);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping generated document cleanup for non-UUID document id {}", documentId);
        } catch (RestClientException e) {
            log.warn("Generated document cleanup failed for document id {}", documentId, e);
        }
    }
}
