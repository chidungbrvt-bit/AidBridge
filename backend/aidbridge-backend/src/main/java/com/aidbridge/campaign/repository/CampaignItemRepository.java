package com.aidbridge.campaign.repository;

import com.aidbridge.campaign.entity.CampaignItem;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignItemRepository extends JpaRepository<CampaignItem, UUID> {
    List<CampaignItem> findByCampaign_IdAndOrganizationIdOrderByCreatedAtAscIdAsc(
            UUID campaignId,
            UUID organizationId
    );
    List<CampaignItem> findByCampaign_IdInOrderByCreatedAtAscIdAsc(Collection<UUID> campaignIds);

    /**
     * Locks the requested items of one campaign. ORDER BY id in SQL fixes the lock order for every
     * caller (Java UUID ordering differs from PostgreSQL). Items of other campaigns are simply absent.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CampaignItem ci WHERE ci.campaign.id = :campaignId AND ci.id IN :ids ORDER BY ci.id")
    List<CampaignItem> lockByIds(@Param("campaignId") UUID campaignId, @Param("ids") Collection<UUID> ids);

    interface ItemTotal {
        UUID getItemId();
        Long getQuantity();
    }

    /**
     * Public read model: active pledged quantity per campaign item (absent = 0). Native SQL on purpose,
     * so the campaign module does not import the contribution module (contribution depends on campaign).
     * Same formula as ContributionItemRepository.activeTotals. Callers must pass a non-empty collection.
     */
    @Query(value = """
            SELECT ci.campaign_item_id AS "itemId",
                   SUM(ci.pledged_quantity - ci.cancelled_quantity) AS "quantity"
            FROM contribution_items ci
            WHERE ci.campaign_item_id IN (:ids)
            GROUP BY ci.campaign_item_id
            """, nativeQuery = true)
    List<ItemTotal> activePledgedTotals(@Param("ids") Collection<UUID> campaignItemIds);
}