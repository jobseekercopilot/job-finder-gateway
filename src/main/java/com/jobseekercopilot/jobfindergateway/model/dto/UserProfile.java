package com.jobseekercopilot.jobfindergateway.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public class UserProfile {
    private String userId;
    
    @JsonProperty("skills")
    private String skills;
    
    @JsonProperty("experience")
    private String experience;
    
    @JsonProperty("aspirations")
    private String aspirationsJson;
    
    @JsonProperty("workPrefs")
    private String workPrefsJson;
    
    // Parsed aspirations fields (flattened)
    private List<String> desiredRoles;
    private List<String> industries;
    private Integer salaryMin;
    private Integer salaryMax;
    private String salaryCurrency;
    private List<String> locations;
    
    // Parsed work preferences fields (flattened)
    private List<String> employmentType;
    private String remotePreference;
    private List<String> companySize;
    private List<String> culture;

    public UserProfile() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public String getAspirationsJson() {
        return aspirationsJson;
    }

    public void setAspirationsJson(String aspirationsJson) {
        this.aspirationsJson = aspirationsJson;
    }

    public String getWorkPrefsJson() {
        return workPrefsJson;
    }

    public void setWorkPrefsJson(String workPrefsJson) {
        this.workPrefsJson = workPrefsJson;
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

    public Integer getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(Integer salaryMin) {
        this.salaryMin = salaryMin;
    }

    public Integer getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(Integer salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public void setSalaryCurrency(String salaryCurrency) {
        this.salaryCurrency = salaryCurrency;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
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