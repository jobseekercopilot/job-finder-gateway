package com.jobseekercopilot.jobfindergateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateApplicationStatusRequest(
        @NotBlank(message = "status is required")
        @Schema(
                description = "New application status",
                example = "APPLIED",
                allowableValues = {
                        "DOCUMENTS_GENERATED",
                        "APPLIED",
                        "INTERVIEW",
                        "UNSUCCESSFUL",
                        "OFFER",
                        "ACCEPTED",
                        "REJECTED_BY_USER",
                        "WITHDRAWN"
                },
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String status,
        @PositiveOrZero(message = "expectedVersion must be zero or greater")
        @Schema(
                description = "Record version last observed by the caller; required when status is APPLIED",
                example = "3",
                minimum = "0")
        Long expectedVersion
) {
}
