package com.aidbridge.contribution.repository;

import com.aidbridge.contribution.entity.ContributionItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContributionItemRepository extends JpaRepository<ContributionItem, UUID> {

    /** One aggregated quantity per item ID. Items without rows are absent; callers default to 0. */
    interface ItemQuantity {
        UUID getItemId();
        Long getQuantity();
    }

    /**
     * activePledged = SUM(pledged - cancelled) per campaign item, in one query for many items.
     * Only meaningful for capacity checks when the campaign item rows are already locked (T2).
     * Callers must pass a non-empty collection.
     */
    @Query("""
            SELECT ci.campaignItemId AS itemId,
                   SUM(ci.pledgedQuantity - ci.cancelledQuantity) AS quantity
            FROM ContributionItem ci
            WHERE ci.campaignItemId IN :campaignItemIds
            GROUP BY ci.campaignItemId
            """)
    List<ItemQuantity> activeTotals(@Param("campaignItemIds") Collection<UUID> campaignItemIds);

    /**
     * Received quantity per contribution item. Native because goods receipts have no entity before B08;
     * read-only here. Quoted aliases keep camelCase keys (PostgreSQL lowercases unquoted aliases).
     * Callers must pass a non-empty collection.
     */
    @Query(value = """
            SELECT gri.contribution_item_id AS "itemId",
                   SUM(gri.received_quantity) AS "quantity"
            FROM goods_receipt_items gri
            WHERE gri.contribution_item_id IN (:contributionItemIds)
            GROUP BY gri.contribution_item_id
            """, nativeQuery = true)
    List<ItemQuantity> receivedTotals(@Param("contributionItemIds") Collection<UUID> contributionItemIds);

    interface LineRef {
        UUID getLineId();
        UUID getCampaignItemId();
    }

    /** Scalars only (F5). The set of lines never changes after the pledge, so reading it before locking is safe. */
    @Query("""
            SELECT ci.id AS lineId, ci.campaignItemId AS campaignItemId
            FROM ContributionItem ci
            WHERE ci.contribution.id = :contributionId
            """)
    List<LineRef> findLineRefs(@Param("contributionId") UUID contributionId);

    /** Locks every line of one contribution; ORDER BY id in SQL fixes the lock order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM ContributionItem ci WHERE ci.contribution.id = :contributionId ORDER BY ci.id")
    List<ContributionItem> lockByContribution(@Param("contributionId") UUID contributionId);

    /** All lines of many contributions in display order (name, unit, id) in one query. Read-only (G02). */
    @Query("""
            SELECT ci FROM ContributionItem ci, CampaignItem item
            WHERE item.id = ci.campaignItemId AND ci.contribution.id IN :contributionIds
            ORDER BY lower(item.itemName), lower(item.unit), item.id
            """)
    List<ContributionItem> findDisplayOrdered(@Param("contributionIds") Collection<UUID> contributionIds);
}