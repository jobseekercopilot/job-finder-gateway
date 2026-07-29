package com.jobseekercopilot.jobfindergateway.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Non-sensitive identity and digest of a validated claim ledger")
public record ValidatedClaimLedgerReference(
        UUID ledgerId,
        String ledgerSha256,
        String policyVersion,
        String parserVersion) {
}
