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
        long getDeliveredQty();
        long getConfirmedQty();
    }

    /**
     * Read both totals from one statement so concurrent commits cannot mix their snapshots.
     * Aggregate receipts separately to avoid counting a pledge once per receipt.
     */
    @Query(value = """
        WITH contribution_progress AS (
            SELECT ci.campaign_item_id,
                   SUM(ci.pledged_quantity - ci.cancelled_quantity) AS pledged_qty,
                   SUM(COALESCE(receipts.total, 0))::bigint AS received_qty
            FROM contribution_items ci
            LEFT JOIN LATERAL (
                SELECT SUM(gri.received_quantity) AS total
                FROM goods_receipt_items gri
                WHERE gri.contribution_item_id = ci.id
            ) receipts ON true
            WHERE ci.campaign_item_id IN (:ids)
            GROUP BY ci.campaign_item_id
        ),
        delivery_progress AS (
            SELECT di.campaign_item_id,
                   SUM(di.delivered_quantity) AS delivered_qty,
                   SUM(di.delivered_quantity) FILTER (
                       WHERE EXISTS (
                           SELECT 1
                           FROM delivery_confirmations dc
                           WHERE dc.delivery_id = d.id
                             AND dc.decision = 'CONFIRMED'
                       )
                   ) AS confirmed_qty
            FROM delivery_items di
            JOIN deliveries d ON d.id = di.delivery_id
            WHERE di.campaign_item_id IN (:ids)
              AND d.outcome = 'DELIVERED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM delivery_voids dv
                  WHERE dv.delivery_id = d.id
              )
            GROUP BY di.campaign_item_id
        )
        SELECT item.id AS "itemId",
               COALESCE(cp.pledged_qty, 0) AS "pledgedQty",
               COALESCE(cp.received_qty, 0) AS "receivedQty",
               COALESCE(dp.delivered_qty, 0) AS "deliveredQty",
               COALESCE(dp.confirmed_qty, 0) AS "confirmedQty"
        FROM campaign_items item
        LEFT JOIN contribution_progress cp ON cp.campaign_item_id = item.id
        LEFT JOIN delivery_progress dp ON dp.campaign_item_id = item.id
        WHERE item.id IN (:ids)
        """, nativeQuery = true)
    List<ItemProgress> progressTotals(@Param("ids") Collection<UUID> campaignItemIds);
}
