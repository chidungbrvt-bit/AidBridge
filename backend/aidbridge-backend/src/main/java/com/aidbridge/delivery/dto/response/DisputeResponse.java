package com.aidbridge.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisputeResponse(
        UUID id,
        UUID caseId,
        UUID deliveryId,
        String status,
        String reason,
        String resolution,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {
}