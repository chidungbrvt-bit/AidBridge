package com.aidbridge.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConfirmationResponse(
        UUID id,
        UUID deliveryId,
        String decision,
        UUID reviewerId,
        Instant reviewedAt,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        UUID disputeId,

        List<GoodsTotal> goodsTotals,
        UUID auditEventId,
        String blockchainStatus
) {
    public record GoodsTotal(
            UUID campaignItemId,
            String itemName,
            String unit,
            int targetQty,
            long pledgedQty,
            long receivedQty,
            long deliveredQty,
            long confirmedQty,
            long availableToDeliverQty
    ) {
    }
}