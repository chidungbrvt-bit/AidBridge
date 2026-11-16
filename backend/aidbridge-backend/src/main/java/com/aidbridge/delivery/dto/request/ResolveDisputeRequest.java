package com.aidbridge.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record ResolveDisputeRequest(
        @NotNull
        @Pattern(regexp = "UPHOLD|VOID_DELIVERY")
        String outcome,

        @NotBlank
        @Size(max = 2000)
        @Pattern(regexp = "[^<>\\x00]*", message = "Reason must be plain text.")
        String reason,

        @NotNull
        List<@NotNull UUID> reviewEvidenceVersionIds
) {
    public ResolveDisputeRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Review evidence version IDs must be distinct.")
    public boolean isDistinctReviewEvidenceVersions() {
        return reviewEvidenceVersionIds == null
                || new HashSet<>(reviewEvidenceVersionIds).size()
                == reviewEvidenceVersionIds.size();
    }
}