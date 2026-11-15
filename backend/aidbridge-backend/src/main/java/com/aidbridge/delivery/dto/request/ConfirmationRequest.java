package com.aidbridge.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record ConfirmationRequest(
        @NotNull
        @Pattern(regexp = "CONFIRMED|REQUIRES_REVIEW")
        String decision,

        @NotEmpty
        List<@NotNull UUID> reviewedEvidenceVersionIds,

        @Size(max = 2000)
        String reason
) {
    public ConfirmationRequest {
        if (reason != null) {
            reason = reason.strip();
            if (reason.isEmpty()) {
                reason = null;
            }
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Reason is required when review is required.")
    public boolean isReasonValidForDecision() {
        return !"REQUIRES_REVIEW".equals(decision) || reason != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Reviewed evidence version IDs must be distinct.")
    public boolean isDistinctReviewedEvidenceVersions() {
        return reviewedEvidenceVersionIds == null
                || new HashSet<>(reviewedEvidenceVersionIds).size()
                == reviewedEvidenceVersionIds.size();
    }

    @JsonIgnore
    @AssertTrue(message = "Reason must be plain text.")
    public boolean isPlainTextReason() {
        return reason == null
                || (reason.indexOf('<') < 0
                && reason.indexOf('>') < 0
                && reason.indexOf('\0') < 0);
    }
}