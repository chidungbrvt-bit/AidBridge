package com.aidbridge.campaign.dto.response;

import com.aidbridge.campaign.entity.Campaign;
import java.util.List;
import java.util.UUID;

public record CampaignResponse(
        UUID id,
        Campaign.Status status,
        String title,
        String publicSummary,
        String approximateArea,
        OrganizationSummary organization,
        List<ItemSummary> items,
        String auditUrl
) {
    public record OrganizationSummary(
            String name,
            String publicRef
    ) {
    }

    public record ItemSummary(
            UUID id,
            String name,
            String unit,
            int targetQty,
            int pledgedQty,
            int receivedQty,
            int deliveredQty,
            int confirmedQty,
            int remainingPledgeQty
    ) {
    }
}
