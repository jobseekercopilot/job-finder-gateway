package com.jobseekercopilot.jobfindergateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stable public gateway error")
public record ApiErrorResponse(
        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        String schemaVersion,
        @Schema(
                example = "JOB_FINDER_INVALID_REQUEST",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(
                example = "The request does not meet the documented requirements.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String correlationId) {
}
