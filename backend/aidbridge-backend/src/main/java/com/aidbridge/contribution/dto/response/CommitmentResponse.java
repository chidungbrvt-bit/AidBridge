package com.aidbridge.contribution.dto.response;

import com.aidbridge.contribution.entity.Contribution;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owned by the donor. Shape frozen with F02 (docs/fixtures/f02/goods-flow.types.ts). */
public record CommitmentResponse(
        UUID id,
        UUID campaignId,
        List<Item> items,
        Contribution.Status status,
        Instant createdAt,
        UUID auditEventId,
        String blockchainStatus
) {
    public record Item(
            UUID id,
            UUID campaignItemId,
            int pledgedQty,
            int cancelledQty,
            int receivedQty,
            int outstandingQty
    ) {
    }
}
