package com.aidbridge.casefile.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StaffCaseDetailResponse(
        @JsonUnwrapped CaseDetailResponse caseDetail,
        UUID campaignId,
        List<OutstandingCommitment> outstandingCommitments
) {
    public record OutstandingCommitment(
            UUID contributionId, String status, Instant pledgedAt, List<Item> items
    ) {
    }

    public record Item(
            UUID contributionItemId, UUID campaignItemId, String itemName, String unit,
            int pledgedQty, long receivedQty, long outstandingQty
    ) {
    }
}
