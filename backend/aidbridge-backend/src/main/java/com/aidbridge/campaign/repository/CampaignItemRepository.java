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

    interface ItemProgress {
        UUID getItemId();
        long getPledgedQty();
        long getReceivedQty();
    }

    /**
     * Read both totals from one statement so concurrent commits cannot mix their snapshots.
     * Aggregate receipts separately to avoid counting a pledge once per receipt.
     */
    @Query(value = """
            SELECT ci.campaign_item_id AS "itemId",
                   SUM(ci.pledged_quantity - ci.cancelled_quantity) AS "pledgedQty",
                   SUM(COALESCE(r.total, 0))::bigint AS "receivedQty"
            FROM contribution_items ci
            LEFT JOIN LATERAL (
                SELECT SUM(gri.received_quantity) AS total
                FROM goods_receipt_items gri WHERE gri.contribution_item_id = ci.id
            ) r ON true
            WHERE ci.campaign_item_id IN (:ids)
            GROUP BY ci.campaign_item_id
            """, nativeQuery = true)
    List<ItemProgress> progressTotals(@Param("ids") Collection<UUID> campaignItemIds);
}
