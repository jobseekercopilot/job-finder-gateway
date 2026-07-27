package com.jobseekercopilot.jobfindergateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public class JobSearchRequest {
    private Aspirations aspirations;
    private WorkPreferences workPreferences;
    private HomeLocation homeLocation;
    private List<String> selectedProviders;
    @Schema(
            description = "One-based aggregate result page. Omitted values use the Job Service default.",
            minimum = "1",
            maximum = "100",
            defaultValue = "1"
    )
    private Integer page;
    @Schema(
            description = "Maximum aggregate results returned on one page. Omitted values use the Job Service default.",
            minimum = "1",
            maximum = "50",
            defaultValue = "10"
    )
    private Integer pageSize;
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

    public static class Aspirations {
        private List<String> desiredRoles;
        private List<String> industries;
        private SalaryExpectation salaryExpectation;
        private List<String> locations;

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
        private Integer min;
        private Integer max;
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
    }

    public static class WorkPreferences {
        private List<String> employmentType;
        private String remotePreference;
        private List<String> companySize;
        private List<String> culture;
        private Double homeLatitude;
        private Double homeLongitude;

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
    }

    public static class HomeLocation {
        private String displayName;
        private String postcode;
        private Double latitude;
        private Double longitude;

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
    }
}
