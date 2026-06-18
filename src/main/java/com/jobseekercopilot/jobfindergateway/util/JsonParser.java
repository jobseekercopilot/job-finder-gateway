package com.jobseekercopilot.jobfindergateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.jobfindergateway.model.dto.UserProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JsonParser {

    private final ObjectMapper objectMapper;

    public JsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void parseAspirationsAndWorkPrefs(UserProfile userProfile) {
        if (userProfile.getAspirationsJson() != null && !userProfile.getAspirationsJson().isEmpty()) {
            parseAspirations(userProfile);
        }
        
        if (userProfile.getWorkPrefsJson() != null && !userProfile.getWorkPrefsJson().isEmpty()) {
            parseWorkPrefs(userProfile);
        }
    }

    private void parseAspirations(UserProfile userProfile) {
        try {
            JsonNode root = objectMapper.readTree(userProfile.getAspirationsJson());
            
            // Parse desiredRoles
            if (root.has("desiredRoles")) {
                userProfile.setDesiredRoles(parseStringList(root.get("desiredRoles")));
            }
            
            // Parse industries
            if (root.has("industries")) {
                userProfile.setIndustries(parseStringList(root.get("industries")));
            }
            
            // Parse salaryExpectation
            if (root.has("salaryExpectation")) {
                JsonNode salary = root.get("salaryExpectation");
                if (salary.has("min")) {
                    userProfile.setSalaryMin(salary.get("min").asInt());
                }
                if (salary.has("max")) {
                    userProfile.setSalaryMax(salary.get("max").asInt());
                }
                if (salary.has("currency")) {
                    userProfile.setSalaryCurrency(salary.get("currency").asText());
                }
            }
            
            // Parse locations
            if (root.has("locations")) {
                userProfile.setLocations(parseStringList(root.get("locations")));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse aspirations JSON", e);
        }
    }

    private void parseWorkPrefs(UserProfile userProfile) {
        try {
            JsonNode root = objectMapper.readTree(userProfile.getWorkPrefsJson());
            
            // Parse employmentType
            if (root.has("employmentType")) {
                userProfile.setEmploymentType(parseStringList(root.get("employmentType")));
            }
            
            // Parse remotePreference
            if (root.has("remotePreference")) {
                userProfile.setRemotePreference(root.get("remotePreference").asText());
            }
            
            // Parse companySize
            if (root.has("companySize")) {
                userProfile.setCompanySize(parseStringList(root.get("companySize")));
            }
            
            // Parse culture
            if (root.has("culture")) {
                userProfile.setCulture(parseStringList(root.get("culture")));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse work preferences JSON", e);
        }
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode element : node) {
                list.add(element.asText());
            }
        }
        return list;
    }
}