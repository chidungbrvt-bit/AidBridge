package com.aidbridge.campaign.repository;

import com.aidbridge.campaign.entity.CampaignItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignItemRepository extends JpaRepository<CampaignItem, UUID> {
    List<CampaignItem> findByCampaign_IdAndOrganizationIdOrderByCreatedAtAscIdAsc(
            UUID campaignId,
            UUID organizationId
    );
    List<CampaignItem> findByCampaign_IdInOrderByCreatedAtAscIdAsc(Collection<UUID> campaignIds);
}