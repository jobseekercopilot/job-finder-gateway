package com.jobseekercopilot.jobfindergateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.generated.jobservice.model.Job;
import com.jobseekercopilot.generated.jobservice.model.ReedJobSearchResponse;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceCategory;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceConfirmationState;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceEntry;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceLifecycle;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceRevision;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceVisibility;
import com.jobseekercopilot.generated.userprofileservice.model.PartialDate;
import com.jobseekercopilot.generated.userprofileservice.model.UserProfile;
import com.jobseekercopilot.jobfindergateway.client.JobServiceClientFactory;
import com.jobseekercopilot.jobfindergateway.client.UserProfileClientFactory;
import com.jobseekercopilot.jobfindergateway.http.DownstreamFailureResponses;
import com.jobseekercopilot.jobfindergateway.logging.CorrelationIdFilter;
import com.jobseekercopilot.jobfindergateway.model.dto.ApiErrorResponse;
import com.jobseekercopilot.jobfindergateway.model.dto.ApplicationRecordResponse;
import com.jobseekercopilot.jobfindergateway.model.dto.CreateTrackedApplicationRequest;
import com.jobseekercopilot.jobfindergateway.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobfindergateway.model.dto.UpdateApplicationStatusRequest;
import com.jobseekercopilot.jobfindergateway.model.dto.WithdrawGeneratedApplicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Job Search", description = "Job search orchestration endpoints")
public class JobSearchController {

    private static final Logger log = LoggerFactory.getLogger(JobSearchController.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private final UserProfileClientFactory userProfileClientFactory;
    private final JobServiceClientFactory jobServiceClientFactory;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String applicationTrackerBaseUrl;

    public JobSearchController(UserProfileClientFactory userProfileClientFactory,
                               JobServiceClientFactory jobServiceClientFactory,
                               ObjectMapper objectMapper,
                               RestTemplate restTemplate,
                               @Value("${services.application-tracker.url}") String applicationTrackerBaseUrl) {
        this.userProfileClientFactory = userProfileClientFactory;
        this.jobServiceClientFactory = jobServiceClientFactory;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.applicationTrackerBaseUrl = applicationTrackerBaseUrl;
    }

    @PostMapping("/search")
    @Operation(summary = "Search jobs", description = "Searches for jobs using either a provided search request or the user's profile preferences.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReedJobSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or incomplete user profile",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Job search request body is too large",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Downstream service unavailable",
                    content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Search")
    public ResponseEntity<?> searchJobs(
            @AuthenticationPrincipal Jwt accessToken,
            @Parameter(description = "Job search request parameters (optional - if omitted, uses user profile preferences)", required = false)
            @Valid @RequestBody(required = false) JobSearchRequest searchRequest) {
        long startedAt = System.nanoTime();
        String userId = accessToken.getSubject();
        log.info("job-finder-gateway received job search userId={} requestSource={}",
                userId,
                searchRequest == null ? "PROFILE" : "REQUEST_BODY");

        // If no search request body provided, fall back to profile-based search
        if (searchRequest == null) {
            com.jobseekercopilot.generated.userprofileservice.model.UserProfile userProfile;
            try {
                long profileStartedAt = System.nanoTime();
                log.info("Calling user-profile-service for job search userId={}", userId);
                userProfile = userProfileClientFactory
                        .authenticated(accessToken.getTokenValue())
                        .getMyProfile();
                log.info("user-profile-service returned profile for job search userId={} durationMs={}",
                        userId,
                        (System.nanoTime() - profileStartedAt) / 1_000_000);
            } catch (RestClientException e) {
                log.warn("user-profile-service failed for job search userId={} durationMs={} error={}",
                        userId,
                        (System.nanoTime() - startedAt) / 1_000_000,
                        e.getClass().getSimpleName(),
                        e);
                return downstreamFailure(
                        e,
                        "JOB_FINDER_PROFILE_TIMEOUT",
                        "The user profile service did not respond in time.",
                        "JOB_FINDER_INVALID_PROFILE_RESPONSE",
                        "The user profile service returned an invalid response.",
                        "JOB_FINDER_PROFILE_UNAVAILABLE",
                        "The user profile service is currently unavailable.");
            }

            if (userProfile == null) {
                log.warn("job-finder-gateway job search rejected userId={} reason=ProfileNotFound durationMs={}",
                        userId,
                        (System.nanoTime() - startedAt) / 1_000_000);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiErrorResponse(
                                "1",
                                "JOB_FINDER_PROFILE_NOT_FOUND",
                                "The user profile does not exist.",
                                CorrelationIdFilter.currentCorrelationId()));
            }

            if (desiredRoles(userProfile).isEmpty() || locations(userProfile).isEmpty()) {
                log.warn("job-finder-gateway job search rejected userId={} reason=IncompleteProfile roles={} locations={} durationMs={}",
                        userId,
                        desiredRoles(userProfile).size(),
                        locations(userProfile).size(),
                        (System.nanoTime() - startedAt) / 1_000_000);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiErrorResponse(
                                "1",
                                "JOB_FINDER_INVALID_PROFILE",
                                "The user profile needs target roles and locations before searching.",
                                CorrelationIdFilter.currentCorrelationId()));
            }

            try {
                ResponseEntity<ReedJobSearchResponse> response =
                        searchDownstream(
                                accessToken,
                                fromProfile(
                                        userProfile,
                                        confirmedEvidenceOrEmpty(
                                                accessToken,
                                                userId)));
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
                return jobServiceFailure(e);
            }
        }

        // Use the request body from the frontend (preferred flow)
        try {
            // Matching evidence is an owner-bound server projection. Values in
            // a request body are never trusted as claimant evidence.
            searchRequest.setCandidateProfile(
                    ownerCandidateProfileOrNull(accessToken, userId));
            ResponseEntity<ReedJobSearchResponse> response =
                    searchDownstream(accessToken, searchRequest);
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
            return jobServiceFailure(e);
        }
    }

    @GetMapping("/provider/{provider}/{externalJobId}")
    @Operation(
            summary = "Get complete provider job details",
            description = "Fetches a selected job's generation-time description without hydrating every search result.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job details returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Job.class))),
            @ApiResponse(responseCode = "404", description = "Provider details are not available",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Job Service unavailable",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<?> getJobDetails(
            @AuthenticationPrincipal Jwt accessToken,
            @PathVariable String provider,
            @PathVariable String externalJobId) {
        try {
            Job details = jobServiceClientFactory
                    .authenticated(accessToken.getTokenValue())
                    .getJobDetails(
                            provider.toUpperCase(Locale.ROOT),
                            externalJobId);
            if (details == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(apiError(
                                "JOB_FINDER_INVALID_JOB_SERVICE_RESPONSE",
                                "The job service returned an invalid response."));
            }
            return ResponseEntity.ok(details);
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(apiError(
                                "JOB_FINDER_JOB_DETAILS_NOT_FOUND",
                                "Complete provider job details are not available."));
            }
            return jobServiceFailure(exception);
        } catch (RestClientException exception) {
            return jobServiceFailure(exception);
        }
    }

    private ResponseEntity<ReedJobSearchResponse> searchDownstream(
            Jwt accessToken,
            JobSearchRequest request) {
        long startedAt = System.nanoTime();
        String userId = accessToken.getSubject();
        int roles = request.getAspirations() != null
                && request.getAspirations().getDesiredRoles() != null
                ? request.getAspirations().getDesiredRoles().size()
                : 0;
        log.info("Calling job-service /api/jobs/search userId={} targetRoles={}", userId, roles);
        var generatedRequest = objectMapper.convertValue(
                request,
                com.jobseekercopilot.generated.jobservice.model.JobSearchRequest.class);
        ReedJobSearchResponse response = jobServiceClientFactory
                .authenticated(accessToken.getTokenValue())
                .searchJobs(generatedRequest);
        log.info("job-service returned status=200 userId={} totalResults={} durationMs={}",
                userId,
                response == null ? null : response.getTotalResults(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/applications")
    @Operation(
            summary = "Track an application for the authenticated claimant",
            description = "Creates a manual application while deriving ownership exclusively from the validated access token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Application created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApplicationRecordResponse.class))),
            @ApiResponse(responseCode = "200", description = "Existing idempotent application returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApplicationRecordResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid application request",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Application tracker unavailable",
                    content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Applications")
    public ResponseEntity<?> createApplication(
            @AuthenticationPrincipal Jwt accessToken,
            @Valid @RequestBody CreateTrackedApplicationRequest request) {
        long startedAt = System.nanoTime();
        String subject = accessToken.getSubject();
        var trackerRequest = new LinkedHashMap<String, Object>();
        trackerRequest.put("userId", subject);
        trackerRequest.put("jobId", request.jobId());
        trackerRequest.put("canonicalJobId", request.canonicalJobId());
        trackerRequest.put("provider", request.provider());
        trackerRequest.put("externalJobId", request.externalJobId());
        trackerRequest.put("jobTitle", request.jobTitle());
        trackerRequest.put("companyName", request.companyName());
        if (request.location() != null && !request.location().isBlank()) {
            trackerRequest.put("location", request.location());
        }
        putIfNotBlank(trackerRequest, "listingUrl", request.listingUrl());
        putIfNotBlank(trackerRequest, "applyUrl", request.applyUrl());
        putIfNotBlank(trackerRequest, "attributionLabel", request.attributionLabel());
        putIfNotBlank(trackerRequest, "attributionSourceUrl", request.attributionSourceUrl());
        putIfNotBlank(trackerRequest, "licenceUrl", request.licenceUrl());
        putIfNotBlank(trackerRequest, "disclaimer", request.disclaimer());
        trackerRequest.put("provenance", "MANUAL");
        trackerRequest.put("initialStatus", "SAVED");

        try {
            ResponseEntity<Object> response = restTemplate.exchange(
                    applicationTrackerBaseUrl + "/api/v1/applications",
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(trackerRequest, authenticatedHeaders(accessToken)),
                    Object.class);
            ApplicationRecordResponse created =
                    objectMapper.convertValue(response.getBody(), ApplicationRecordResponse.class);
            if (!isOwnedBy(created, subject)) {
                log.error("application-tracker-service create failed ownership validation durationMs={}",
                        (System.nanoTime() - startedAt) / 1_000_000);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(apiError(
                                "JOB_FINDER_INVALID_APPLICATION_RESPONSE",
                                "The application service returned an invalid response."));
            }
            log.info("application-tracker-service create returned responseStatus={} durationMs={}",
                    response.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity.status(response.getStatusCode()).body(created);
        } catch (HttpStatusCodeException e) {
            log.warn("application-tracker-service create rejected responseStatus={} durationMs={}",
                    e.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return sanitizedApplicationFailure(e);
        } catch (RestClientException e) {
            log.warn("application-tracker-service create failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    e.getClass().getSimpleName(),
                    e);
            return applicationTrackerFailure(e);
        }
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    @GetMapping("/applications")
    @Operation(
            summary = "Get applications for the authenticated claimant",
            description = "Derives the application owner exclusively from the validated access token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Applications returned",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ApplicationRecordResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Application tracker unavailable",
                    content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Applications")
    public ResponseEntity<?> getApplications(
            @AuthenticationPrincipal Jwt accessToken) {
        return getApplicationsForSubject(accessToken);
    }

    @GetMapping("/applications/user/{userId}")
    @Operation(
            summary = "Get persisted applications for the authenticated user",
            description = "Proxies only the authenticated subject's application records from application-tracker-service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Applications returned",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ApplicationRecordResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Application owner not found",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "502", description = "Downstream ownership validation failed",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Application tracker unavailable", content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Applications")
    public ResponseEntity<?> getApplicationsForUser(
            @AuthenticationPrincipal Jwt accessToken,
            @Parameter(description = "Authenticated subject; must match the access token")
            @PathVariable String userId) {
        long startedAt = System.nanoTime();
        String subject = accessToken.getSubject();
        if (!subject.equals(userId)) {
            log.warn("application list ownership check denied durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);
            return applicationNotFound();
        }
        return getApplicationsForSubject(accessToken);
    }

    private ResponseEntity<?> getApplicationsForSubject(Jwt accessToken) {
        long startedAt = System.nanoTime();
        String subject = accessToken.getSubject();
        try {
            log.info("Calling application-tracker-service application list");
            ResponseEntity<List<ApplicationRecordResponse>> response = restTemplate.exchange(
                    applicationTrackerBaseUrl + "/api/v1/applications/user/{subject}",
                    org.springframework.http.HttpMethod.GET,
                    authenticatedEntity(accessToken),
                    new ParameterizedTypeReference<List<ApplicationRecordResponse>>() {},
                    subject);
            List<ApplicationRecordResponse> records =
                    response.getBody() == null ? List.of() : response.getBody();
            if (records.stream().anyMatch(record -> !isOwnedBy(record, subject))) {
                log.error("application-tracker-service list failed ownership validation durationMs={}",
                        (System.nanoTime() - startedAt) / 1_000_000);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(apiError(
                                "JOB_FINDER_INVALID_APPLICATION_RESPONSE",
                                "The application service returned an invalid response."));
            }
            log.info("application-tracker-service list returned responseStatus={} count={} durationMs={}",
                    response.getStatusCode().value(),
                    records.size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity.status(response.getStatusCode()).body(records);
        } catch (RestClientException e) {
            log.warn("application-tracker-service list failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    e.getClass().getSimpleName(),
                    e);
            return applicationTrackerFailure(e);
        }
    }

    @PatchMapping("/applications/{applicationId}/status")
    @Operation(summary = "Update job application status", description = "Proxies a job-card status update to application-tracker-service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApplicationRecordResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status request", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Application record not found", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "502", description = "Downstream ownership validation failed", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Application tracker unavailable", content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Applications")
    public ResponseEntity<?> updateApplicationStatus(
            @AuthenticationPrincipal Jwt accessToken,
            @Parameter(description = "Application tracker record ID") @PathVariable UUID applicationId,
            @Parameter(description = "Status update request") @jakarta.validation.Valid @RequestBody UpdateApplicationStatusRequest request,
            @Parameter(
                    description = "Required replay-safe command key when status is APPLIED",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = 128,
                            pattern = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey) {
        long startedAt = System.nanoTime();
        String normalizedStatus = request.status().toUpperCase(Locale.ROOT);
        if (!supportedStatuses().contains(normalizedStatus)) {
            log.warn("job-finder-gateway application status rejected status={} durationMs={}",
                    normalizedStatus,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(apiError(
                            "JOB_FINDER_INVALID_APPLICATION_STATUS",
                            "The application status is not supported."));
        }
        if ("APPLIED".equals(normalizedStatus)
                && (idempotencyKey == null
                || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(apiError(
                            "JOB_FINDER_INVALID_IDEMPOTENCY_KEY",
                            "A valid Idempotency-Key is required when marking an application applied."));
        }
        if ("APPLIED".equals(normalizedStatus) && request.expectedVersion() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(apiError(
                            "JOB_FINDER_INVALID_EXPECTED_VERSION",
                            "The current application version is required when marking an application applied."));
        }

        try {
            requireOwnedApplication(accessToken, applicationId);
            log.info("Calling application-tracker-service status update status={}", normalizedStatus);
            HttpHeaders downstreamHeaders = authenticatedHeaders(accessToken);
            if ("APPLIED".equals(normalizedStatus)) {
                downstreamHeaders.set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
            }
            ResponseEntity<Object> response = restTemplate.exchange(
                    applicationTrackerBaseUrl + "/api/v1/applications/" + applicationId + "/status",
                    org.springframework.http.HttpMethod.PATCH,
                    new HttpEntity<>(
                            new UpdateApplicationStatusRequest(
                                    normalizedStatus,
                                    request.expectedVersion()),
                            downstreamHeaders),
                    Object.class);
            ApplicationRecordResponse updated =
                    objectMapper.convertValue(response.getBody(), ApplicationRecordResponse.class);
            if (!isOwnedBy(updated, accessToken.getSubject())) {
                log.error("application-tracker-service status response failed ownership validation durationMs={}",
                        (System.nanoTime() - startedAt) / 1_000_000);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(apiError(
                                "JOB_FINDER_INVALID_APPLICATION_RESPONSE",
                                "The application service returned an invalid response."));
            }
            log.info("application-tracker-service status update returned status={} responseStatus={} durationMs={}",
                    normalizedStatus,
                    response.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity
                    .status(response.getStatusCode())
                    .body(updated);
        } catch (ApplicationNotFoundException e) {
            log.warn("application status ownership check denied durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);
            return applicationNotFound();
        } catch (HttpStatusCodeException e) {
            log.warn("application-tracker-service status update rejected responseStatus={} durationMs={}",
                    e.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return sanitizedApplicationFailure(e);
        } catch (RestClientException e) {
            log.warn("application-tracker-service status update failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    e.getClass().getSimpleName(),
                    e);
            return applicationTrackerFailure(e);
        }
    }

    @PostMapping("/applications/{applicationId}/withdraw-generated")
    @Operation(
            summary = "Withdraw generated application",
            description = "Delegates the durable cleanup workflow to Application Tracker and preserves its completed or recovery-pending outcome.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Generated application withdrawn",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WithdrawGeneratedApplicationResponse.class))),
            @ApiResponse(responseCode = "202", description = "Withdrawal accepted and awaiting recoverable document cleanup",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WithdrawGeneratedApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Application has already progressed and cannot be reset", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Application record not found", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "502", description = "Downstream ownership validation failed", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Application tracker unavailable", content = @Content(mediaType = "application/json"))
    })
    @Tag(name = "Job Applications")
    public ResponseEntity<?> withdrawGeneratedApplication(
            @AuthenticationPrincipal Jwt accessToken,
            @Parameter(description = "Application tracker record ID") @PathVariable UUID applicationId) {
        long startedAt = System.nanoTime();
        log.info("job-finder-gateway generated application withdraw received");
        try {
            log.info("Calling application-tracker-service withdraw generated");
            ResponseEntity<Object> response = restTemplate.exchange(
                    applicationTrackerBaseUrl + "/api/v1/applications/" + applicationId + "/withdraw-generated",
                    org.springframework.http.HttpMethod.POST,
                    authenticatedEntity(accessToken),
                    Object.class);
            WithdrawGeneratedApplicationResponse result = objectMapper.convertValue(
                    response.getBody(),
                    WithdrawGeneratedApplicationResponse.class);
            if (result == null || !applicationId.equals(result.applicationId())) {
                log.error("application-tracker-service withdraw response failed resource validation durationMs={}",
                        (System.nanoTime() - startedAt) / 1_000_000);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(apiError(
                                "JOB_FINDER_INVALID_APPLICATION_RESPONSE",
                                "The application service returned an invalid response."));
            }
            log.info("Generated application withdraw resolved responseStatus={} workflowStatus={} durationMs={}",
                    response.getStatusCode().value(),
                    result.operationStatus(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ResponseEntity
                    .status(response.getStatusCode())
                    .body(result);
        } catch (ApplicationNotFoundException e) {
            log.warn("generated application withdraw ownership check denied durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);
            return applicationNotFound();
        } catch (HttpStatusCodeException e) {
            log.warn("Generated application withdraw rejected responseStatus={} durationMs={}",
                    e.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return sanitizedApplicationFailure(e);
        } catch (RestClientException e) {
            log.warn("Generated application withdraw failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    e.getClass().getSimpleName(),
                    e);
            return applicationTrackerFailure(e);
        }
    }

    private JobSearchRequest fromProfile(
            UserProfile profile,
            List<EvidenceEntry> evidence) {
        var aspirations = new JobSearchRequest.Aspirations();
        aspirations.setDesiredRoles(desiredRoles(profile));
        aspirations.setIndustries(List.of());
        aspirations.setLocations(locations(profile));

        var workPreferences = new JobSearchRequest.WorkPreferences();
        workPreferences.setEmploymentType(employmentTypes(profile));
        workPreferences.setCompanySize(List.of());
        workPreferences.setCulture(List.of());
        if (profile.getWorkPreferences().getCommuteTravelModes() != null) {
            workPreferences.setCommuteTravelModes(profile.getWorkPreferences().getCommuteTravelModes().stream()
                    .map(Enum::name).toList());
        }
        workPreferences.setMaximumDrivingMinutes(profile.getWorkPreferences().getMaximumDrivingMinutes());
        workPreferences.setMaximumTransitMinutes(profile.getWorkPreferences().getMaximumTransitMinutes());
        workPreferences.setMaximumDistanceMiles(profile.getWorkPreferences().getCommuteRange());
        if (profile.getWorkPreferences().getWorkplaceArrangements() != null) {
            workPreferences.setWorkplaceArrangements(profile.getWorkPreferences().getWorkplaceArrangements().stream()
                    .map(Enum::name).toList());
        }

        var sourceLocation = profile.getWorkPreferences().getLocation();
        workPreferences.setHomeLatitude(sourceLocation.getLatitude());
        workPreferences.setHomeLongitude(sourceLocation.getLongitude());

        var homeLocation = new JobSearchRequest.HomeLocation();
        homeLocation.setLocationId(sourceLocation.getLocationId() == null
                ? null
                : sourceLocation.getLocationId().toString());
        homeLocation.setDisplayName(firstNonBlank(sourceLocation.getDisplayName(),
                firstNonBlank(sourceLocation.getAdminDistrict(), sourceLocation.getRegion())));
        homeLocation.setPostcode(sourceLocation.getPostcode());
        homeLocation.setLatitude(sourceLocation.getLatitude());
        homeLocation.setLongitude(sourceLocation.getLongitude());
        homeLocation.setCountryCode(sourceLocation.getCountryCode());
        homeLocation.setPrecision(sourceLocation.getPrecision() == null ? null : sourceLocation.getPrecision().name());
        homeLocation.setConfidence(sourceLocation.getConfidence() == null ? null : sourceLocation.getConfidence().name());

        var request = new JobSearchRequest();
        request.setAspirations(aspirations);
        request.setWorkPreferences(workPreferences);
        request.setHomeLocation(homeLocation);
        request.setCandidateProfile(candidateProfile(profile, evidence));
        return request;
    }

    private JobSearchRequest.CandidateProfile ownerCandidateProfileOrNull(
            Jwt accessToken,
            String userId) {
        UserProfile profile;
        try {
            profile = userProfileClientFactory
                    .authenticated(accessToken.getTokenValue())
                    .getMyProfile();
        } catch (RestClientException exception) {
            log.warn(
                    "Owner profile evidence unavailable for job matching userId={} error={}; continuing with title-only matching",
                    userId,
                    exception.getClass().getSimpleName());
            return null;
        }
        return candidateProfile(
                profile,
                confirmedEvidenceOrEmpty(accessToken, userId));
    }

    private List<EvidenceEntry> confirmedEvidenceOrEmpty(
            Jwt accessToken,
            String userId) {
        try {
            List<EvidenceEntry> evidence = userProfileClientFactory
                    .authenticatedEvidence(accessToken.getTokenValue())
                    .listEvidence(false);
            return evidence == null ? List.of() : evidence;
        } catch (RestClientException exception) {
            log.warn(
                    "Confirmed evidence unavailable for job matching userId={} error={}; continuing with bounded profile skills only",
                    userId,
                    exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private JobSearchRequest.CandidateProfile candidateProfile(
            UserProfile profile,
            List<EvidenceEntry> evidence) {
        List<EvidenceRevisionView> confirmed = confirmedEvidence(evidence);
        LinkedHashMap<String, String> skills = new LinkedHashMap<>();
        if (profile != null && profile.getSkills() != null) {
            profile.getSkills().forEach(value -> addCanonicalSkill(skills, value));
        }
        confirmed.stream()
                .map(EvidenceRevisionView::revision)
                .map(EvidenceRevision::getDemonstratedSkills)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .forEach(value -> addCanonicalSkill(skills, value));

        List<JobSearchRequest.CandidateRole> roles = confirmed.stream()
                .filter(view -> view.category() == EvidenceCategory.EMPLOYMENT
                        || view.category() == EvidenceCategory.FREELANCE)
                .map(EvidenceRevisionView::revision)
                .map(this::candidateRole)
                .filter(Objects::nonNull)
                .limit(50)
                .toList();

        List<JobSearchRequest.CandidateQualification> qualifications =
                confirmed.stream()
                        .filter(view -> view.category() == EvidenceCategory.EDUCATION
                                || view.category()
                                        == EvidenceCategory.QUALIFICATION_TRAINING)
                        .map(this::candidateQualification)
                        .filter(Objects::nonNull)
                        .limit(50)
                        .toList();

        if (skills.isEmpty() && roles.isEmpty() && qualifications.isEmpty()) {
            return null;
        }
        var candidate = new JobSearchRequest.CandidateProfile();
        candidate.setSkills(skills.values().stream().limit(100).toList());
        candidate.setRoles(roles);
        candidate.setQualifications(qualifications);
        return candidate;
    }

    private List<EvidenceRevisionView> confirmedEvidence(
            List<EvidenceEntry> evidence) {
        if (evidence == null) {
            return List.of();
        }
        return evidence.stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.getLifecycle() == EvidenceLifecycle.ACTIVE)
                .filter(entry -> entry.getVisibility() == EvidenceVisibility.VISIBLE)
                .filter(entry -> !Boolean.TRUE.equals(entry.getReviewRequired()))
                .map(this::latestRevisionView)
                .filter(Objects::nonNull)
                .filter(view -> view.category() != null)
                .filter(view -> view.revision().getConfirmationState()
                        == EvidenceConfirmationState.USER_CONFIRMED)
                .toList();
    }

    private EvidenceRevisionView latestRevisionView(EvidenceEntry entry) {
        EvidenceRevision latest = latestRevision(entry);
        return latest == null
                ? null
                : new EvidenceRevisionView(entry.getCategory(), latest);
    }

    private EvidenceRevision latestRevision(EvidenceEntry entry) {
        if (entry.getRevisions() == null) {
            return null;
        }
        return entry.getRevisions().stream()
                .filter(Objects::nonNull)
                .filter(revision -> revision.getRevisionNumber() != null)
                .max(Comparator.comparingInt(EvidenceRevision::getRevisionNumber))
                .orElse(null);
    }

    private JobSearchRequest.CandidateRole candidateRole(
            EvidenceRevision revision) {
        String title = boundedText(revision.getRoleTitle(), 200);
        String startDate = candidateDate(revision.getStartDate());
        String endDate = candidateDate(revision.getEndDate());
        String status;
        if (Boolean.TRUE.equals(revision.getOngoing()) && endDate == null) {
            status = "CURRENT";
        } else if (Boolean.FALSE.equals(revision.getOngoing())
                && endDate != null) {
            status = "PREVIOUS_ROLE";
        } else {
            return null;
        }
        if (title == null || startDate == null
                || (endDate != null && month(endDate).isBefore(month(startDate)))) {
            return null;
        }
        var role = new JobSearchRequest.CandidateRole();
        role.setJobTitle(title);
        role.setStatus(status);
        role.setStartDate(startDate);
        role.setEndDate(endDate);
        return role;
    }

    private JobSearchRequest.CandidateQualification candidateQualification(
            EvidenceRevisionView view) {
        EvidenceRevision revision = view.revision();
        String name = view.category() == EvidenceCategory.EDUCATION
                ? boundedText(revision.getProgrammeOrSubject(), 200)
                : boundedText(revision.getQualificationTitle(), 200);
        if (name == null) {
            return null;
        }
        String status;
        String dateAchieved = null;
        String expectedCompletion = null;
        if (view.category() == EvidenceCategory.EDUCATION) {
            if (Boolean.TRUE.equals(revision.getOngoing())) {
                status = "IN_PROGRESS";
                expectedCompletion = candidateDate(revision.getEndDate());
                if (expectedCompletion == null) return null;
            } else if (Boolean.FALSE.equals(revision.getOngoing())) {
                status = "COMPLETED";
                dateAchieved = firstNonBlank(
                        candidateDate(revision.getEndDate()),
                        candidateDate(revision.getIssueDate()));
                if (dateAchieved == null) return null;
            } else {
                return null;
            }
        } else if (revision.getIssueDate() != null) {
            status = "COMPLETED";
            dateAchieved = candidateDate(revision.getIssueDate());
            if (dateAchieved == null) return null;
        } else if (Boolean.TRUE.equals(revision.getOngoing())) {
            status = "IN_PROGRESS";
            expectedCompletion = candidateDate(revision.getEndDate());
            if (expectedCompletion == null) return null;
        } else {
            return null;
        }
        var qualification = new JobSearchRequest.CandidateQualification();
        qualification.setQualificationName(name);
        qualification.setStatus(status);
        qualification.setDateAchieved(dateAchieved);
        qualification.setExpectedCompletion(expectedCompletion);
        return qualification;
    }

    private String candidateDate(PartialDate value) {
        if (value == null || value.getPrecision() == null
                || value.getYear() == null) {
            return null;
        }
        try {
            return switch (value.getPrecision()) {
                case MONTH -> YearMonth.of(value.getYear(), value.getMonth()).toString();
                case DAY -> LocalDate.of(
                        value.getYear(), value.getMonth(), value.getDay()).toString();
                // Matching is month-based. Omitting year-only dates is safer
                // than inventing a month or an exact day.
                case YEAR -> null;
            };
        } catch (DateTimeException | NullPointerException exception) {
            return null;
        }
    }

    private YearMonth month(String value) {
        return YearMonth.parse(value.substring(0, 7));
    }

    private String boundedText(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximumLength ? trimmed : null;
    }

    private void addCanonicalSkill(
            LinkedHashMap<String, String> skills,
            String value) {
        String skill = boundedText(value, 100);
        if (skill != null && skills.size() < 100) {
            skills.putIfAbsent(skill.toLowerCase(Locale.ROOT), skill);
        }
    }

    private record EvidenceRevisionView(
            EvidenceCategory category,
            EvidenceRevision revision) {
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
        var preferences = profile.getWorkPreferences();
        Set<String> mapped = new LinkedHashSet<>();
        if (preferences != null && preferences.getEmploymentTypes() != null) {
            preferences.getEmploymentTypes().stream()
                    .map(Enum::name)
                    .filter(value -> value.equals("CONTRACT") || value.equals("TEMPORARY"))
                    .forEach(mapped::add);
        }
        if (preferences != null && preferences.getWorkingPatterns() != null) {
            preferences.getWorkingPatterns().stream()
                    .map(Enum::name)
                    .filter(value -> value.equals("FULL_TIME") || value.equals("PART_TIME"))
                    .forEach(mapped::add);
        }
        if (!mapped.isEmpty()) {
            return List.copyOf(mapped);
        }
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

    private ApplicationRecordResponse requireOwnedApplication(Jwt accessToken, UUID applicationId) {
        ResponseEntity<Object> existing = restTemplate.exchange(
                applicationTrackerBaseUrl + "/api/v1/applications/" + applicationId,
                org.springframework.http.HttpMethod.GET,
                authenticatedEntity(accessToken),
                Object.class);
        ApplicationRecordResponse record =
                objectMapper.convertValue(existing.getBody(), ApplicationRecordResponse.class);
        if (!isOwnedBy(record, accessToken.getSubject())) {
            throw new ApplicationNotFoundException();
        }
        return record;
    }

    private boolean isOwnedBy(ApplicationRecordResponse record, String subject) {
        return record != null && subject.equals(record.userId());
    }

    private HttpHeaders authenticatedHeaders(Jwt accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken.getTokenValue());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private HttpEntity<Void> authenticatedEntity(Jwt accessToken) {
        return new HttpEntity<>(authenticatedHeaders(accessToken));
    }

    private ResponseEntity<ApiErrorResponse> applicationNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(apiError(
                        "JOB_FINDER_APPLICATION_NOT_FOUND",
                        "The application record was not found."));
    }

    private ResponseEntity<ApiErrorResponse> sanitizedApplicationFailure(
            HttpStatusCodeException exception) {
        if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                || exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
            return applicationNotFound();
        }
        if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
                || exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(apiError(
                            "JOB_FINDER_APPLICATION_OPERATION_REJECTED",
                            "The application operation was rejected."));
        }
        return DownstreamFailureResponses.unavailable(
                "JOB_FINDER_APPLICATION_TRACKER_UNAVAILABLE",
                "The application service is currently unavailable.");
    }

    private ResponseEntity<ApiErrorResponse> jobServiceFailure(Exception failure) {
        return downstreamFailure(
                failure,
                "JOB_FINDER_JOB_SERVICE_TIMEOUT",
                "The job service did not respond in time.",
                "JOB_FINDER_INVALID_JOB_SERVICE_RESPONSE",
                "The job service returned an invalid response.",
                "JOB_FINDER_JOB_SERVICE_UNAVAILABLE",
                "The job service is currently unavailable.");
    }

    private ResponseEntity<ApiErrorResponse> applicationTrackerFailure(
            Exception failure) {
        return downstreamFailure(
                failure,
                "JOB_FINDER_APPLICATION_TRACKER_TIMEOUT",
                "The application service did not respond in time.",
                "JOB_FINDER_INVALID_APPLICATION_RESPONSE",
                "The application service returned an invalid response.",
                "JOB_FINDER_APPLICATION_TRACKER_UNAVAILABLE",
                "The application service is currently unavailable.");
    }

    private ResponseEntity<ApiErrorResponse> downstreamFailure(
            Exception failure,
            String timeoutCode,
            String timeoutMessage,
            String malformedCode,
            String malformedMessage,
            String unavailableCode,
            String unavailableMessage) {
        if (DownstreamFailureResponses.isTimeout(failure)) {
            return DownstreamFailureResponses.timeout(timeoutCode, timeoutMessage);
        }
        if (DownstreamFailureResponses.isMalformedResponse(failure)) {
            return DownstreamFailureResponses.badGateway(
                    malformedCode,
                    malformedMessage);
        }
        return DownstreamFailureResponses.unavailable(
                unavailableCode,
                unavailableMessage);
    }

    private ApiErrorResponse apiError(String code, String message) {
        return new ApiErrorResponse(
                "1",
                code,
                message,
                CorrelationIdFilter.currentCorrelationId());
    }

    private static final class ApplicationNotFoundException extends RuntimeException {
    }
}
