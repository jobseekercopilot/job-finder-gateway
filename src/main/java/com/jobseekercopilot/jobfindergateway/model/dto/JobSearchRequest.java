package com.jobseekercopilot.jobfindergateway.model.dto;

import java.util.List;

public class JobSearchRequest {
    private Aspirations aspirations;
    private WorkPreferences workPreferences;

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
    }
}