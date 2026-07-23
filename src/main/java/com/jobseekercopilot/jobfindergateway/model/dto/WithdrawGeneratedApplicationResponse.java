package com.jobseekercopilot.jobfindergateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Response returned when generated application documents are withdrawn before applying")
public record WithdrawGeneratedApplicationResponse(
        UUID applicationId,
        String status,
        boolean withdrawn,
        String message
) {
}
