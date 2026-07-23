package com.jobseekercopilot.jobfindergateway.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Public response contract mirrored from job-service. */
public class JobSearchResponse {
    private List<Job> jobs;
    private List<TargetRoleJobResults> resultsByTargetRole;
    private Integer totalResults;
    private Integer page;
    private Integer pageSize;
    private List<ProviderResultStatus> providerResults;

    public List<Job> getJobs() { return jobs; }
    public void setJobs(List<Job> jobs) { this.jobs = jobs; }
    public List<TargetRoleJobResults> getResultsByTargetRole() { return resultsByTargetRole; }
    public void setResultsByTargetRole(List<TargetRoleJobResults> resultsByTargetRole) { this.resultsByTargetRole = resultsByTargetRole; }
    public Integer getTotalResults() { return totalResults; }
    public void setTotalResults(Integer totalResults) { this.totalResults = totalResults; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public List<ProviderResultStatus> getProviderResults() { return providerResults; }
    public void setProviderResults(List<ProviderResultStatus> providerResults) { this.providerResults = providerResults; }

    public static class Job {
        private String id;
        private String canonicalJobId;
        private String provider;
        private String primarySource;
        private String externalJobId;
        private String title;
        private String jobTitle;
        private String company;
        private String companyName;
        private String location;
        private Salary salary;
        private String employmentType;
        private String postedDate;
        private String postedAt;
        private Double distanceMiles;
        private String description;
        private String url;
        private String sourceUrl;
        private List<JobSourceReference> sources;
        private Double matchScore;
        private String applicationStatus;
        private UUID applicationId;
        private String cvDocumentId;
        private String coverLetterDocumentId;
        private LocalDateTime appliedAt;
        private LocalDateTime applicationUpdatedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCanonicalJobId() { return canonicalJobId; }
        public void setCanonicalJobId(String canonicalJobId) { this.canonicalJobId = canonicalJobId; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getPrimarySource() { return primarySource; }
        public void setPrimarySource(String primarySource) { this.primarySource = primarySource; }
        public String getExternalJobId() { return externalJobId; }
        public void setExternalJobId(String externalJobId) { this.externalJobId = externalJobId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public Salary getSalary() { return salary; }
        public void setSalary(Salary salary) { this.salary = salary; }
        public String getEmploymentType() { return employmentType; }
        public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
        public String getPostedDate() { return postedDate; }
        public void setPostedDate(String postedDate) { this.postedDate = postedDate; }
        public String getPostedAt() { return postedAt; }
        public void setPostedAt(String postedAt) { this.postedAt = postedAt; }
        public Double getDistanceMiles() { return distanceMiles; }
        public void setDistanceMiles(Double distanceMiles) { this.distanceMiles = distanceMiles; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getSourceUrl() { return sourceUrl; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
        public List<JobSourceReference> getSources() { return sources; }
        public void setSources(List<JobSourceReference> sources) { this.sources = sources; }
        public Double getMatchScore() { return matchScore; }
        public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }
        public String getApplicationStatus() { return applicationStatus; }
        public void setApplicationStatus(String applicationStatus) { this.applicationStatus = applicationStatus; }
        public UUID getApplicationId() { return applicationId; }
        public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
        public String getCvDocumentId() { return cvDocumentId; }
        public void setCvDocumentId(String cvDocumentId) { this.cvDocumentId = cvDocumentId; }
        public String getCoverLetterDocumentId() { return coverLetterDocumentId; }
        public void setCoverLetterDocumentId(String coverLetterDocumentId) { this.coverLetterDocumentId = coverLetterDocumentId; }
        public LocalDateTime getAppliedAt() { return appliedAt; }
        public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
        public LocalDateTime getApplicationUpdatedAt() { return applicationUpdatedAt; }
        public void setApplicationUpdatedAt(LocalDateTime applicationUpdatedAt) { this.applicationUpdatedAt = applicationUpdatedAt; }
    }

    public static class TargetRoleJobResults {
        private String targetRole;
        private List<Job> jobs;

        public String getTargetRole() { return targetRole; }
        public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
        public List<Job> getJobs() { return jobs; }
        public void setJobs(List<Job> jobs) { this.jobs = jobs; }
    }

    public static class Salary {
        private Integer min;
        private Integer max;
        private String currency;
        private String period;
        private Double normalisedAnnualMinimum;
        private Double normalisedAnnualMaximum;
        private Double normalisedAnnualMidpoint;

        public Integer getMin() { return min; }
        public void setMin(Integer min) { this.min = min; }
        public Integer getMax() { return max; }
        public void setMax(Integer max) { this.max = max; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public Double getNormalisedAnnualMinimum() { return normalisedAnnualMinimum; }
        public void setNormalisedAnnualMinimum(Double normalisedAnnualMinimum) { this.normalisedAnnualMinimum = normalisedAnnualMinimum; }
        public Double getNormalisedAnnualMaximum() { return normalisedAnnualMaximum; }
        public void setNormalisedAnnualMaximum(Double normalisedAnnualMaximum) { this.normalisedAnnualMaximum = normalisedAnnualMaximum; }
        public Double getNormalisedAnnualMidpoint() { return normalisedAnnualMidpoint; }
        public void setNormalisedAnnualMidpoint(Double normalisedAnnualMidpoint) { this.normalisedAnnualMidpoint = normalisedAnnualMidpoint; }
    }

    public static class JobSourceReference {
        private String integrationProvider;
        private String provider;
        private String publisher;
        private String externalJobId;
        private String listingUrl;
        private String applyUrl;
        private Boolean directApply;

        public String getIntegrationProvider() { return integrationProvider; }
        public void setIntegrationProvider(String integrationProvider) { this.integrationProvider = integrationProvider; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getPublisher() { return publisher; }
        public void setPublisher(String publisher) { this.publisher = publisher; }
        public String getExternalJobId() { return externalJobId; }
        public void setExternalJobId(String externalJobId) { this.externalJobId = externalJobId; }
        public String getListingUrl() { return listingUrl; }
        public void setListingUrl(String listingUrl) { this.listingUrl = listingUrl; }
        public String getApplyUrl() { return applyUrl; }
        public void setApplyUrl(String applyUrl) { this.applyUrl = applyUrl; }
        public Boolean getDirectApply() { return directApply; }
        public void setDirectApply(Boolean directApply) { this.directApply = directApply; }
    }

    public static class ProviderResultStatus {
        private String provider;
        private String status;
        private int rawResultCount;
        private String errorMessage;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getRawResultCount() { return rawResultCount; }
        public void setRawResultCount(int rawResultCount) { this.rawResultCount = rawResultCount; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
