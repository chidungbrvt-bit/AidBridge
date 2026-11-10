package com.aidbridge.campaign.mapper;

import com.aidbridge.campaign.dto.response.CampaignResponse;
import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.entity.CampaignItem;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CampaignMapper {
    public CampaignResponse toResponse(Campaign campaign, List<CampaignItem> items) {
        var org = campaign.getOrganization();
        String publicRefHex = "0x" + HexFormat.of().formatHex(org.getPublicRef());
        var orgSummary = new CampaignResponse.OrganizationSummary(org.getName(), publicRefHex);

        var itemSummaries = items.stream()
                .map(item -> new CampaignResponse.ItemSummary(
                        item.getId(),
                        item.getItemName(),
                        item.getUnit(),
                        item.getTargetQuantity(),
                        0,
                        0,
                        0,
                        0,
                        item.getTargetQuantity()
                ))
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
