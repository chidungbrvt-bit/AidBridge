package com.aidbridge.delivery.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Only the information needed to review the specified unresolved dispute. */
public record DisputeReviewResponse(
        UUID id, UUID caseId, String status, String reason, Instant createdAt, Delivery delivery
) {
    public record Delivery(
            UUID id, Instant recordedAt, List<Item> items, List<UUID> evidenceVersionIds
    ) { }

    public record Item(UUID campaignItemId, String itemName, String unit, int quantity) { }
}
