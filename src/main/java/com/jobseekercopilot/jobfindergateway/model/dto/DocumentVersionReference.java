package com.jobseekercopilot.jobfindergateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Exact immutable document version and evidence descriptor")
public record DocumentVersionReference(
        UUID documentId,
        UUID documentFamilyId,
        String jobId,
        String documentType,
        Integer version,
        String contentSha256,
        DocumentEvidenceProvenance evidenceProvenance,
        DocumentGroundingState groundingState,
        UUID parentDocumentId,
        Integer parentDocumentVersion) {
}
