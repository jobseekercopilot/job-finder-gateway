package com.jobseekercopilot.jobfindergateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Job details used to create an application for the authenticated claimant")
public record CreateTrackedApplicationRequest(
        @NotBlank
        @Size(max = 255)
        String jobId,
        @NotBlank
        @Size(max = 255)
        String canonicalJobId,
        @NotBlank
        @Size(max = 64)
        String provider,
        @NotBlank
        @Size(max = 255)
        String externalJobId,
        @NotBlank
        @Size(max = 300)
        String jobTitle,
        @NotBlank
        @Size(max = 300)
        String companyName,
        @Size(max = 300)
        String location,
        @Size(max = 2048)
        String listingUrl,
        @Size(max = 2048)
        String applyUrl,
        @Size(max = 255)
        String attributionLabel,
        @Size(max = 2048)
        String attributionSourceUrl,
        @Size(max = 2048)
        String licenceUrl,
        @Size(max = 1000)
        String disclaimer
) {
}
