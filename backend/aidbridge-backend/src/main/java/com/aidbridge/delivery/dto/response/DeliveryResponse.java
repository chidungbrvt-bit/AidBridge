package com.aidbridge.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID caseId,
        UUID recorderId,
        String outcome,
        String reviewStatus,
        List<Item> items,
        List<UUID> evidenceVersionIds,
        Instant recordedAt,
        UUID auditEventId,
        String blockchainStatus,
        @JsonInclude(JsonInclude.Include.ALWAYS) String auditUrl
) {
    public record Item(UUID campaignItemId, int quantity) {
    }
}