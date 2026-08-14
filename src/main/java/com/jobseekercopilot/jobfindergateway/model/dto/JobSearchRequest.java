package com.jobseekercopilot.jobfindergateway.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public class JobSearchRequest {

    @NotNull(message = "aspirations is required")
    @Valid
    private Aspirations aspirations;

    @Valid
    private WorkPreferences workPreferences;

    @Valid
    private HomeLocation homeLocation;

    @Valid
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(
            accessMode = Schema.AccessMode.READ_ONLY,
            description = "Server-derived minimal claimant evidence. Caller-supplied values are ignored.")
    private CandidateProfile candidateProfile;

    @Size(max = 5, message = "selectedProviders cannot contain more than 5 values")
    private List<
            @NotBlank(message = "selectedProviders cannot contain blank values")
            @Pattern(
                    regexp = "(?i)\\s*(REED|ADZUNA|JSEARCH|NHS_JOBS|APPRENTICESHIPS)\\s*",
                    message = "selectedProviders contains an unsupported provider")
            String> selectedProviders;

    @Min(value = 1, message = "page must be at least 1")
    @Max(value = 100, message = "page cannot exceed 100")
    @Schema(
            description = "One-based aggregate result page. Omitted values use the Job Service default.",
            minimum = "1",
            maximum = "100",
            defaultValue = "1"
    )
    private Integer page;

    @Min(value = 1, message = "pageSize must be at least 1")
    @Max(value = 50, message = "pageSize cannot exceed 50")
    @Schema(
            description = "Maximum aggregate results returned on one page. Omitted values use the Job Service default.",
            minimum = "1",
            maximum = "50",
            defaultValue = "10"
    )
    private Integer pageSize;

    @Pattern(
            regexp = "(?i)\\s*(MOST_RELEVANT|CLOSEST|HIGHEST_SALARY|NEWEST_POSTED"
                    + "|OLDEST_POSTED|COMPANY_AZ|JOB_TITLE_AZ)\\s*",
            message = "sort contains an unsupported value")
    @Schema(
            description = "Stable Job Service aggregate result order.",
            allowableValues = {
                    "MOST_RELEVANT",
                    "CLOSEST",
                    "HIGHEST_SALARY",
                    "NEWEST_POSTED",
                    "OLDEST_POSTED",
                    "COMPANY_AZ",
                    "JOB_TITLE_AZ"
            },
            defaultValue = "MOST_RELEVANT"
    )
    private String sort;

    public JobSearchRequest() {
    }

    public Aspirations getAspirations() {
        return aspirations;
    }

    public void setAspirations(Aspirations aspirations) {
        this.aspirations = aspirations;
    }

    public WorkPreferences getWorkPreferences() {
        return workPreferences;
    }

    public void setWorkPreferences(WorkPreferences workPreferences) {
        this.workPreferences = workPreferences;
    }

    public HomeLocation getHomeLocation() {
        return homeLocation;
    }

    public void setHomeLocation(HomeLocation homeLocation) {
        this.homeLocation = homeLocation;
    }

    public CandidateProfile getCandidateProfile() { return candidateProfile; }
    public void setCandidateProfile(CandidateProfile value) { candidateProfile = value; }

    public List<String> getSelectedProviders() {
        return selectedProviders;
    }

    public void setSelectedProviders(List<String> selectedProviders) {
        this.selectedProviders = selectedProviders;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    @Schema(description = "Minimal claimant-declared evidence for deterministic matching; no inferred eligibility facts are accepted.")
    public static class CandidateProfile {
        @Size(max = 100)
        private List<@NotBlank @Size(max = 100) String> skills;
        @Valid @Size(max = 50)
        private List<CandidateRole> roles;
        @Valid @Size(max = 50)
        private List<CandidateQualification> qualifications;
        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> value) { skills = value; }
        public List<CandidateRole> getRoles() { return roles; }
        public void setRoles(List<CandidateRole> value) { roles = value; }
        public List<CandidateQualification> getQualifications() { return qualifications; }
        public void setQualifications(List<CandidateQualification> value) { qualifications = value; }
    }

    public static class CandidateRole {
        @NotBlank @Size(max = 200)
        private String jobTitle;
        @NotBlank @Pattern(regexp = "CURRENT|PREVIOUS_ROLE")
        private String status;
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}(?:-\\d{2})?")
        private String startDate;
        @Pattern(regexp = "\\d{4}-\\d{2}(?:-\\d{2})?")
        private String endDate;
        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String value) { jobTitle = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String value) { startDate = value; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String value) { endDate = value; }
    }

    public static class CandidateQualification {
        @NotBlank @Size(max = 200)
        private String qualificationName;
        @NotBlank @Pattern(regexp = "IN_PROGRESS|COMPLETED")
        private String status;
        @Pattern(regexp = "\\d{4}-\\d{2}(?:-\\d{2})?")
        private String dateAchieved;
        @Pattern(regexp = "\\d{4}-\\d{2}(?:-\\d{2})?")
        private String expectedCompletion;
        public String getQualificationName() { return qualificationName; }
        public void setQualificationName(String value) { qualificationName = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getDateAchieved() { return dateAchieved; }
        public void setDateAchieved(String value) { dateAchieved = value; }
        public String getExpectedCompletion() { return expectedCompletion; }
        public void setExpectedCompletion(String value) { expectedCompletion = value; }
    }

    public static class Aspirations {

        @NotEmpty(message = "aspirations.desiredRoles is required")
        @Size(
                min = 1,
                max = 10,
                message = "aspirations.desiredRoles must contain between 1 and 10 values")
        private List<
                @NotBlank(message = "aspirations.desiredRoles cannot contain blank values")
                @Size(
                        min = 1,
                        max = 120,
                        message = "aspirations.desiredRoles values must contain 1 to 120 characters")
                String> desiredRoles;

        @Size(max = 20, message = "aspirations.industries cannot contain more than 20 values")
        private List<
                @NotBlank(message = "aspirations.industries cannot contain blank values")
                @Size(
                        min = 1,
                        max = 120,
                        message = "aspirations.industries values must contain 1 to 120 characters")
                String> industries;

        @Valid
        private SalaryExpectation salaryExpectation;

        @NotEmpty(message = "aspirations.locations is required")
        @Size(
                min = 1,
                max = 10,
                message = "aspirations.locations must contain between 1 and 10 values")
        private List<
                @NotBlank(message = "aspirations.locations cannot contain blank values")
                @Size(
                        min = 1,
                        max = 200,
                        message = "aspirations.locations values must contain 1 to 200 characters")
                String> locations;

        public Aspirations() {
        }

        public List<String> getDesiredRoles() {
            return desiredRoles;
        }

        public void setDesiredRoles(List<String> desiredRoles) {
            this.desiredRoles = desiredRoles;
        }

        public List<String> getIndustries() {
            return industries;
        }

        public void setIndustries(List<String> industries) {
            this.industries = industries;
        }

        public SalaryExpectation getSalaryExpectation() {
            return salaryExpectation;
        }

        public void setSalaryExpectation(SalaryExpectation salaryExpectation) {
            this.salaryExpectation = salaryExpectation;
        }

        public List<String> getLocations() {
            return locations;
        }

        public void setLocations(List<String> locations) {
            this.locations = locations;
        }
    }

    public static class SalaryExpectation {

        @Min(value = 0, message = "salaryExpectation.min cannot be negative")
        @Max(value = 10_000_000, message = "salaryExpectation.min is unreasonably large")
        private Integer min;

        @Min(value = 0, message = "salaryExpectation.max cannot be negative")
        @Max(value = 10_000_000, message = "salaryExpectation.max is unreasonably large")
        private Integer max;

        @Pattern(
                regexp = "(?i)[A-Z]{3}",
                message = "salaryExpectation.currency must be a three-letter currency code")
        private String currency;

        public SalaryExpectation() {
        }

        public Integer getMin() {
            return min;
        }

        public void setMin(Integer min) {
            this.min = min;
        }

        public Integer getMax() {
            return max;
        }

        public void setMax(Integer max) {
            this.max = max;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        @AssertTrue(message = "salaryExpectation.min cannot exceed salaryExpectation.max")
        @JsonIgnore
        public boolean isRangeValid() {
            return min == null || max == null || min <= max;
        }
    }

    public static class WorkPreferences {

        @Size(max = 4, message = "workPreferences.employmentType cannot contain more than 4 values")
        private List<
                @NotBlank(message = "workPreferences.employmentType cannot contain blank values")
                @Pattern(
                        regexp = "(?i)\\s*(FULL_TIME|PART_TIME|CONTRACT|TEMPORARY)\\s*",
                        message = "workPreferences.employmentType contains an unsupported value")
                String> employmentType;

        @Pattern(
                regexp = "(?i)\\s*(REMOTE|HYBRID|ONSITE|ON_SITE)\\s*",
                message = "workPreferences.remotePreference contains an unsupported value")
        private String remotePreference;

        @Size(max = 10, message = "workPreferences.companySize cannot contain more than 10 values")
        private List<
                @NotBlank(message = "workPreferences.companySize cannot contain blank values")
                @Size(
                        min = 1,
                        max = 100,
                        message = "workPreferences.companySize values must contain 1 to 100 characters")
                String> companySize;

        @Size(max = 20, message = "workPreferences.culture cannot contain more than 20 values")
        private List<
                @NotBlank(message = "workPreferences.culture cannot contain blank values")
                @Size(
                        min = 1,
                        max = 100,
                        message = "workPreferences.culture values must contain 1 to 100 characters")
                String> culture;

        @DecimalMin(value = "-90.0", message = "workPreferences.homeLatitude must be at least -90")
        @DecimalMax(value = "90.0", message = "workPreferences.homeLatitude cannot exceed 90")
        private Double homeLatitude;

        @DecimalMin(value = "-180.0", message = "workPreferences.homeLongitude must be at least -180")
        @DecimalMax(value = "180.0", message = "workPreferences.homeLongitude cannot exceed 180")
        private Double homeLongitude;

        @Size(max = 2, message = "workPreferences.commuteTravelModes cannot contain more than 2 values")
        private List<@Pattern(regexp = "DRIVE|TRANSIT") String> commuteTravelModes;

        @Min(5) @Max(180)
        private Integer maximumDrivingMinutes;

        @Min(5) @Max(180)
        private Integer maximumTransitMinutes;

        @Min(0) @Max(500)
        private Integer maximumDistanceMiles;

        @Size(max = 3)
        private List<@Pattern(regexp = "ONSITE|HYBRID|REMOTE") String> workplaceArrangements;

        public WorkPreferences() {
        }

        public List<String> getEmploymentType() {
            return employmentType;
        }

        public void setEmploymentType(List<String> employmentType) {
            this.employmentType = employmentType;
        }

        public String getRemotePreference() {
            return remotePreference;
        }

        public void setRemotePreference(String remotePreference) {
            this.remotePreference = remotePreference;
        }

        public List<String> getCompanySize() {
            return companySize;
        }

        public void setCompanySize(List<String> companySize) {
            this.companySize = companySize;
        }

        public List<String> getCulture() {
            return culture;
        }

        public void setCulture(List<String> culture) {
            this.culture = culture;
        }

        public Double getHomeLatitude() {
            return homeLatitude;
        }

        public void setHomeLatitude(Double homeLatitude) {
            this.homeLatitude = homeLatitude;
        }

        public Double getHomeLongitude() {
            return homeLongitude;
        }

        public void setHomeLongitude(Double homeLongitude) {
            this.homeLongitude = homeLongitude;
        }

        public List<String> getCommuteTravelModes() { return commuteTravelModes; }
        public void setCommuteTravelModes(List<String> value) { this.commuteTravelModes = value; }
        public Integer getMaximumDrivingMinutes() { return maximumDrivingMinutes; }
        public void setMaximumDrivingMinutes(Integer value) { this.maximumDrivingMinutes = value; }
        public Integer getMaximumTransitMinutes() { return maximumTransitMinutes; }
        public void setMaximumTransitMinutes(Integer value) { this.maximumTransitMinutes = value; }
        public Integer getMaximumDistanceMiles() { return maximumDistanceMiles; }
        public void setMaximumDistanceMiles(Integer value) { this.maximumDistanceMiles = value; }
        public List<String> getWorkplaceArrangements() { return workplaceArrangements; }
        public void setWorkplaceArrangements(List<String> value) { this.workplaceArrangements = value; }
    }

    public static class HomeLocation {

        @Size(max = 36)
        private String locationId;

        @Size(max = 200, message = "homeLocation.displayName cannot exceed 200 characters")
        private String displayName;

        @Size(max = 16, message = "homeLocation.postcode cannot exceed 16 characters")
        private String postcode;

        @DecimalMin(value = "-90.0", message = "homeLocation.latitude must be at least -90")
        @DecimalMax(value = "90.0", message = "homeLocation.latitude cannot exceed 90")
        private Double latitude;

        @DecimalMin(value = "-180.0", message = "homeLocation.longitude must be at least -180")
        @DecimalMax(value = "180.0", message = "homeLocation.longitude cannot exceed 180")
        private Double longitude;

        @Size(min = 2, max = 2)
        private String countryCode;

        @Pattern(regexp = "EXACT_ADDRESS|POSTCODE_CENTROID|LOCALITY_CENTROID|PROVIDER_COORDINATE|NONE")
        private String precision;

        @Pattern(regexp = "VERIFIED|PROVIDED|INFERRED|AMBIGUOUS|UNKNOWN")
        private String confidence;

        public String getLocationId() { return locationId; }
        public void setLocationId(String locationId) { this.locationId = locationId; }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getPostcode() {
            return postcode;
        }

        public void setPostcode(String postcode) {
            this.postcode = postcode;
        }

        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }

        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
        public String getPrecision() { return precision; }
        public void setPrecision(String precision) { this.precision = precision; }
        public String getConfidence() { return confidence; }
        public void setConfidence(String confidence) { this.confidence = confidence; }
    }
}
