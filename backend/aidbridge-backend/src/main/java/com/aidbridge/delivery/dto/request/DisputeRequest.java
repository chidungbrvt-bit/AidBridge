package com.aidbridge.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DisputeRequest(
        UUID deliveryId,
        @NotBlank
        @Size(max = 2000)
        @Pattern(regexp = "[^<>\\x00]*", message = "Reason must be plain text.")
        String reason
) {
    public DisputeRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}