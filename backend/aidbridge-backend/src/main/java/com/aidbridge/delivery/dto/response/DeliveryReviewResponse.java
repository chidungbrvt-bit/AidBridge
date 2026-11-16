package com.aidbridge.delivery.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeliveryReviewResponse(
        UUID id,
        UUID recorderId,
        String outcome,
        String reviewStatus,
        List<Item> items,
        List<UUID> evidenceVersionIds,
        Instant recordedAt
) {
    public DeliveryReviewResponse {
        items = List.copyOf(items);
        evidenceVersionIds = List.copyOf(evidenceVersionIds);
    }

    public record Item(
            UUID campaignItemId,
            int quantity
    ) {
    }
}