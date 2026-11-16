package com.aidbridge.campaign.mapper;

import com.aidbridge.campaign.dto.response.CampaignResponse;
import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.entity.CampaignItem;
import com.aidbridge.campaign.repository.CampaignItemRepository.ItemProgress;
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
     * Progress counts active pledges and actual receipts separately. Absent items have zero totals.
     */
    public CampaignResponse toResponse(Campaign campaign, List<CampaignItem> items,
            Map<UUID, ItemProgress> progressByItem) {
        var org = campaign.getOrganization();
        String publicRefHex = "0x" + HexFormat.of().formatHex(org.getPublicRef());
        var orgSummary = new CampaignResponse.OrganizationSummary(org.getName(), publicRefHex);

        var itemSummaries = items.stream()
                .map(item -> {
                    var progress = progressByItem.get(item.getId());
                    int pledged = progress == null ? 0 : Math.toIntExact(progress.getPledgedQty());
                    int received = progress == null ? 0 : Math.toIntExact(progress.getReceivedQty());
                    int delivered = progress == null ? 0 : Math.toIntExact(progress.getDeliveredQty());
                    int confirmed = progress == null ? 0 : Math.toIntExact(progress.getConfirmedQty());
                    return new CampaignResponse.ItemSummary(
                            item.getId(),
                            item.getItemName(),
                            item.getUnit(),
                            item.getTargetQuantity(),
                            pledged,
                            received,
                            delivered,
                            confirmed,
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
                "/api/public/cases/0x" + HexFormat.of().formatHex(campaign.getAidCase().getPublicRef()) + "/audit"
        );
    }
}
