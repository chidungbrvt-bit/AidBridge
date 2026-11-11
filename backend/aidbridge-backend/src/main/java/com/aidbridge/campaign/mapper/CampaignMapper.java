package com.aidbridge.campaign.mapper;

import com.aidbridge.campaign.dto.response.CampaignResponse;
import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.entity.CampaignItem;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CampaignMapper {
    /** Draft and publish responses: nothing can be pledged yet, so progress is zero. */
    public CampaignResponse toResponse(Campaign campaign, List<CampaignItem> items) {
        return toResponse(campaign, items, Map.of());
    }

    /**
     * pledgedByItem holds active pledged quantity per campaign item (absent = 0).
     * received/delivered/confirmed stay 0 until B08-B10 record them.
     */
    public CampaignResponse toResponse(Campaign campaign, List<CampaignItem> items, Map<UUID, Long> pledgedByItem) {
        var org = campaign.getOrganization();
        String publicRefHex = "0x" + HexFormat.of().formatHex(org.getPublicRef());
        var orgSummary = new CampaignResponse.OrganizationSummary(org.getName(), publicRefHex);

        var itemSummaries = items.stream()
                .map(item -> {
                    int pledged = Math.toIntExact(pledgedByItem.getOrDefault(item.getId(), 0L));
                    return new CampaignResponse.ItemSummary(
                            item.getId(),
                            item.getItemName(),
                            item.getUnit(),
                            item.getTargetQuantity(),
                            pledged,
                            0,
                            0,
                            0,
                            item.getTargetQuantity() - pledged);
                })
                .toList();

        return new CampaignResponse(
                campaign.getId(),
                campaign.getStatus(),
                campaign.getTitle(),
                campaign.getPublicSummary(),
                campaign.getAidCase().getApproximateArea(),
                orgSummary,
                itemSummaries,
                "/api/campaigns/" + campaign.getId() + "/audit-trail"
        );
    }
}
