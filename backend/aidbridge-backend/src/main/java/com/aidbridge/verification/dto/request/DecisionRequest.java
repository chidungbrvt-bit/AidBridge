package com.aidbridge.verification.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record DecisionRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @Size(max = 2000) String reason,
        @NotEmpty @Size(max = 10) List<@NotNull UUID> reviewedEvidenceVersionIds
) {
    public DecisionRequest {
        if (reason != null) {
            reason = reason.strip();
            if (reason.isEmpty()) {
                reason = null;
            }
        }
    }
}
