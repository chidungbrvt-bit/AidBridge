package com.aidbridge.casefile.dto.response;
import com.aidbridge.casefile.entity.AidCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Private projection. Never reused by public campaign or public audit endpoints. */
public record CaseDetailResponse(
        UUID id,
        UUID organizationId,
        Creator creator,
        String beneficiaryAlias,
        String internalTitle,
        String publicTitle,
        String publicSummary,
        String approximateArea,
        AidCase.Priority priority,
        AidCase.Status status, long version,
        List<Need> needs,
        Consent consent,
        List<EvidenceVersionResponse> evidenceVersions,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        UUID supersedesCaseId) {
    public record Creator(UUID id, String fullName) { }
    public record Need(UUID id, String name, String unit, int quantity) { }
    public record Consent(UUID id, String basis, String scope, Instant capturedAt, boolean publicSummaryAllowed) { }
}
