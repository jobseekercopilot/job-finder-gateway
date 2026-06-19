package com.jobseekercopilot.jobfindergateway.client;

import com.jobseekercopilot.jobfindergateway.model.dto.JobSearchRequest;
import com.jobseekercopilot.jobfindergateway.model.dto.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class JobServiceClient {

    private final RestTemplate restTemplate;
    private final String jobServiceUrl;

    private static final String X_USER_ID_HEADER = "X-User-Id";

    public JobServiceClient(RestTemplate restTemplate,
                            @Value("${services.job-service.url}") String jobServiceUrl) {
        this.restTemplate = restTemplate;
        this.jobServiceUrl = jobServiceUrl;
    }

    /**
     * Search jobs using a direct JobSearchRequest from the frontend.
     */
    public ResponseEntity<String> searchJobs(String userId, JobSearchRequest request) {
        String url = jobServiceUrl + "/api/jobs/search";

        HttpHeaders headers = new HttpHeaders();
        headers.set(X_USER_ID_HEADER, userId);

        HttpEntity<JobSearchRequest> entity = new HttpEntity<>(request, headers);

        try {
            return restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
        } catch (Exception e) {
            throw new ServiceUnavailableException("Job search service is currently unavailable", e);
        }
    }

    /**
     * Search jobs using a UserProfile (convert profile to JobSearchRequest).
     */
    public ResponseEntity<String> searchJobsFromProfile(String userId, UserProfile userProfile) {
        JobSearchRequest request = transformProfileToRequest(userProfile);
        return searchJobs(userId, request);
    }

    private JobSearchRequest transformProfileToRequest(UserProfile profile) {
        JobSearchRequest request = new JobSearchRequest();

        // Build aspirations from flat UserProfile fields
        JobSearchRequest.Aspirations aspirations = new JobSearchRequest.Aspirations();
        aspirations.setDesiredRoles(profile.getDesiredRoles());
        aspirations.setIndustries(profile.getIndustries());
        aspirations.setLocations(profile.getLocations());

        // Build salary expectation from flat fields
        if (profile.getSalaryMin() != null || profile.getSalaryMax() != null || profile.getSalaryCurrency() != null) {
            JobSearchRequest.SalaryExpectation salary = new JobSearchRequest.SalaryExpectation();
            salary.setMin(profile.getSalaryMin());
            salary.setMax(profile.getSalaryMax());
            salary.setCurrency(profile.getSalaryCurrency());
            aspirations.setSalaryExpectation(salary);
        }

        request.setAspirations(aspirations);

        // Build work preferences from flat UserProfile fields
        JobSearchRequest.WorkPreferences workPrefs = new JobSearchRequest.WorkPreferences();
        workPrefs.setEmploymentType(profile.getEmploymentType());
        workPrefs.setRemotePreference(profile.getRemotePreference());
        workPrefs.setCompanySize(profile.getCompanySize());
        workPrefs.setCulture(profile.getCulture());
        request.setWorkPreferences(workPrefs);

        return request;
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}