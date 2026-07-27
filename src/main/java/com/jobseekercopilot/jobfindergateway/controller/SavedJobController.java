package com.jobseekercopilot.jobfindergateway.controller;

import com.jobseekercopilot.generated.jobservice.model.Job;
import com.jobseekercopilot.generated.jobservice.model.SavedJobPageResponse;
import com.jobseekercopilot.generated.jobservice.model.SavedJobResponse;
import com.jobseekercopilot.jobfindergateway.client.JobServiceClientFactory;
import com.jobseekercopilot.jobfindergateway.model.dto.SavedJobErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api/jobs/saved")
@Tag(
        name = "Saved Jobs",
        description = "Owner-scoped saved canonical job operations")
@SecurityRequirement(name = "bearerAuth")
public class SavedJobController {

    private static final Logger log =
            LoggerFactory.getLogger(SavedJobController.class);
    private static final String OUTCOME_HEADER = "X-Saved-Job-Outcome";
    private static final Set<String> CREATED_OUTCOMES = Set.of("CREATED");
    private static final Set<String> REPLAY_OUTCOMES =
            Set.of("REPLAYED", "UPDATED", "REACTIVATED");

    private final JobServiceClientFactory jobServiceClientFactory;

    public SavedJobController(JobServiceClientFactory jobServiceClientFactory) {
        this.jobServiceClientFactory = jobServiceClientFactory;
    }

    @PostMapping
    @Operation(
            summary = "Save a canonical job snapshot",
            description = """
                    Forwards a selected canonical search result to Job Service.
                    The verified access-token subject remains the only owner
                    identity. The returned savedJobId and snapshot metadata are
                    server-owned and must be used by downstream workflows.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Saved job created",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobResponse.class)),
                    headers = @Header(
                            name = OUTCOME_HEADER,
                            schema = @Schema(allowableValues = "CREATED"))),
            @ApiResponse(
                    responseCode = "200",
                    description = "Saved job replayed, updated or reactivated",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobResponse.class)),
                    headers = @Header(
                            name = OUTCOME_HEADER,
                            schema = @Schema(allowableValues = {
                                    "REPLAYED", "UPDATED", "REACTIVATED"
                            }))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid canonical job snapshot",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid access token"),
            @ApiResponse(
                    responseCode = "502",
                    description = "Invalid Job Service response",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobErrorResponse.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "Job Service unavailable",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobErrorResponse.class)))
    })
    public ResponseEntity<?> save(
            @AuthenticationPrincipal Jwt accessToken,
            @RequestBody Job job) {
        try {
            ResponseEntity<SavedJobResponse> downstream =
                    jobServiceClientFactory
                            .authenticatedSavedJobs(accessToken.getTokenValue())
                            .saveWithHttpInfo(job);
            return savedResponse(downstream);
        } catch (HttpStatusCodeException exception) {
            return clientFailure(exception);
        } catch (RestClientException exception) {
            return unavailable(exception);
        }
    }

    @GetMapping
    @Operation(summary = "List the current user's active saved jobs")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Saved jobs returned",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobPageResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid page request",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid access token"),
            @ApiResponse(
                    responseCode = "503",
                    description = "Job Service unavailable",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobErrorResponse.class)))
    })
    public ResponseEntity<?> list(
            @AuthenticationPrincipal Jwt accessToken,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        try {
            ResponseEntity<SavedJobPageResponse> downstream =
                    jobServiceClientFactory
                            .authenticatedSavedJobs(accessToken.getTokenValue())
                            .callListWithHttpInfo(page, size);
            if (downstream.getStatusCode() != HttpStatus.OK
                    || downstream.getBody() == null) {
                return invalidDownstreamResponse();
            }
            return ResponseEntity.ok(downstream.getBody());
        } catch (HttpStatusCodeException exception) {
            return clientFailure(exception);
        } catch (RestClientException exception) {
            return unavailable(exception);
        }
    }

    @GetMapping("/{savedJobId}")
    @Operation(summary = "Retrieve an owner-scoped saved job snapshot")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Saved job returned",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid access token"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Saved job not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobErrorResponse.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "Job Service unavailable",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobErrorResponse.class)))
    })
    public ResponseEntity<?> get(
            @AuthenticationPrincipal Jwt accessToken,
            @PathVariable UUID savedJobId) {
        try {
            ResponseEntity<SavedJobResponse> downstream =
                    jobServiceClientFactory
                            .authenticatedSavedJobs(accessToken.getTokenValue())
                            .getWithHttpInfo(savedJobId);
            if (downstream.getStatusCode() != HttpStatus.OK
                    || downstream.getBody() == null) {
                return invalidDownstreamResponse();
            }
            return ResponseEntity.ok(downstream.getBody());
        } catch (HttpStatusCodeException exception) {
            return clientFailure(exception);
        } catch (RestClientException exception) {
            return unavailable(exception);
        }
    }

    @DeleteMapping("/{savedJobId}")
    @Operation(summary = "Unsave an owner-scoped job")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Saved job is no longer active",
                    content = @Content),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid access token"),
            @ApiResponse(
                    responseCode = "503",
                    description = "Job Service unavailable",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = SavedJobErrorResponse.class)))
    })
    public ResponseEntity<?> unsave(
            @AuthenticationPrincipal Jwt accessToken,
            @PathVariable UUID savedJobId) {
        try {
            ResponseEntity<Void> downstream =
                    jobServiceClientFactory
                            .authenticatedSavedJobs(accessToken.getTokenValue())
                            .unsaveWithHttpInfo(savedJobId);
            if (downstream.getStatusCode() != HttpStatus.NO_CONTENT) {
                return invalidDownstreamResponse();
            }
            return ResponseEntity.noContent().build();
        } catch (HttpStatusCodeException exception) {
            return clientFailure(exception);
        } catch (RestClientException exception) {
            return unavailable(exception);
        }
    }

    private ResponseEntity<?> savedResponse(
            ResponseEntity<SavedJobResponse> downstream) {
        HttpStatus status = HttpStatus.resolve(
                downstream.getStatusCode().value());
        String outcome = downstream.getHeaders().getFirst(OUTCOME_HEADER);
        Set<String> expectedOutcomes = status == HttpStatus.CREATED
                ? CREATED_OUTCOMES
                : status == HttpStatus.OK
                        ? REPLAY_OUTCOMES
                        : Set.of();
        if (downstream.getBody() == null
                || outcome == null
                || !expectedOutcomes.contains(outcome)) {
            return invalidDownstreamResponse();
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(OUTCOME_HEADER, outcome);
        return new ResponseEntity<>(
                downstream.getBody(),
                responseHeaders,
                downstream.getStatusCode());
    }

    private ResponseEntity<?> clientFailure(
            HttpStatusCodeException exception) {
        if (exception.getStatusCode() == HttpStatus.BAD_REQUEST) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SAVED_JOB",
                    "The saved-job request is invalid.");
        }
        if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
            return error(
                    HttpStatus.NOT_FOUND,
                    "SAVED_JOB_NOT_FOUND",
                    "Saved job was not found.");
        }
        return unavailable(exception);
    }

    private ResponseEntity<?> unavailable(Exception exception) {
        log.warn(
                "saved-job request failed category={}",
                exception.getClass().getSimpleName());
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SAVED_JOB_SERVICE_UNAVAILABLE",
                "Saved jobs are currently unavailable.");
    }

    private ResponseEntity<?> invalidDownstreamResponse() {
        log.warn("saved-job request failed category=InvalidDownstreamResponse");
        return error(
                HttpStatus.BAD_GATEWAY,
                "INVALID_SAVED_JOB_RESPONSE",
                "Saved jobs returned an invalid response.");
    }

    private ResponseEntity<SavedJobErrorResponse> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SavedJobErrorResponse(code, message));
    }
}
