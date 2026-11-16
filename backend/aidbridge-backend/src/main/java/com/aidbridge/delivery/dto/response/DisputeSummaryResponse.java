package com.aidbridge.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

public record DisputeSummaryResponse(
        UUID id,

        @JsonInclude(JsonInclude.Include.ALWAYS)
        UUID deliveryId,

        String status,
        String reason,
        Instant createdAt
) {
}