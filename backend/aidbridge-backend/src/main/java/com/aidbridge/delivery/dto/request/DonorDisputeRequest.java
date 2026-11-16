package com.aidbridge.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DonorDisputeRequest(
        @NotBlank
        @Size(max = 2000)
        @Pattern(regexp = "[^<>\\x00]*", message = "Reason must be plain text.")
        String reason
) {
    public DonorDisputeRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}
