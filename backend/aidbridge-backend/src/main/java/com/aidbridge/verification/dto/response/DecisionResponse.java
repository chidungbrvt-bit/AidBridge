package com.aidbridge.verification.dto.response;

import com.aidbridge.casefile.entity.AidCase;
import java.time.Instant;
import java.util.UUID;

public record DecisionResponse(
        UUID verificationId,
        UUID caseId,
        String decision,
        AidCase.Status caseStatus,
        long caseVersion,
        UUID reviewerId,
        Instant reviewedAt,
        UUID auditEventId,
        String blockchainStatus
) {
}