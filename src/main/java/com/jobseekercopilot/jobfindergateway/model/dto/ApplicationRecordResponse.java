package com.jobseekercopilot.jobfindergateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Application tracker record returned after a status update")
public record ApplicationRecordResponse(
        UUID id,
        String userId,
        String jobId,
        String provider,
        String externalJobId,
        String jobTitle,
        String companyName,
        String location,
        String cvDocumentId,
        String coverLetterDocumentId,
        DocumentVersionReference cvDocumentReference,
        DocumentVersionReference coverLetterDocumentReference,
        DocumentVersionReference applicationUsedCvDocumentReference,
        DocumentVersionReference
                applicationUsedCoverLetterDocumentReference,
        LocalDateTime applicationUsedAt,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime appliedAt
) {
}
