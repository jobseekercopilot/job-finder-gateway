package com.jobseekercopilot.jobfindergateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Non-sensitive immutable evidence provenance for a document")
public record DocumentEvidenceProvenance(
        UUID profileRevisionId,
        String profileContentDigest,
        UUID evidenceSnapshotId,
        String evidenceSnapshotDigest,
        List<EvidenceRevisionReference> evidenceRevisions,
        List<EvidenceSection> sectionOrder,
        ValidatedClaimLedgerReference claimLedger,
        OffsetDateTime generatedAt) {
}
