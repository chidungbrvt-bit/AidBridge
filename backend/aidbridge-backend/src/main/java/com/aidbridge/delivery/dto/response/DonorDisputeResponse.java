package com.aidbridge.delivery.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DonorDisputeResponse(
        UUID id,
        String status,
        Instant createdAt
) {
}
