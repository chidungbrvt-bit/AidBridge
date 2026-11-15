package com.aidbridge.casefile.dto.response;

import com.aidbridge.delivery.dto.response.ConfirmationResponse;
import com.aidbridge.delivery.dto.response.DeliveryReviewResponse;
import com.aidbridge.delivery.dto.response.DisputeSummaryResponse;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffCaseDetailResponse(
        @JsonUnwrapped CaseDetailResponse caseDetail,
        UUID campaignId,
        List<OutstandingCommitment> outstandingCommitments,
        List<ConfirmationResponse.GoodsTotal> goodsTotals,
        List<DeliveryReviewResponse> deliveries,
        List<DisputeSummaryResponse> disputes
) {
    public StaffCaseDetailResponse {
        outstandingCommitments = List.copyOf(outstandingCommitments);
        goodsTotals = List.copyOf(goodsTotals);
        deliveries = List.copyOf(deliveries);
        disputes = List.copyOf(disputes);
    }

    public record OutstandingCommitment(
            UUID contributionId,
            String status,
            Instant pledgedAt,
            List<Item> items
    ) {
    }

    public record Item(
            UUID contributionItemId,
            UUID campaignItemId,
            String itemName,
            String unit,
            int pledgedQty,
            long receivedQty,
            long outstandingQty
    ) {
    }
}