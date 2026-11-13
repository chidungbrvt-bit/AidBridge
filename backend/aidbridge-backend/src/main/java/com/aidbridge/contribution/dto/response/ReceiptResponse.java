package com.aidbridge.contribution.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        UUID contributionId,
        List<Item> items,
        Instant receivedAt,
        Instant recordedAt,
        UUID recorderId,
        UUID auditEventId,
        String blockchainStatus
) {
    public record Item(UUID contributionItemId, int quantity) {
    }
}
