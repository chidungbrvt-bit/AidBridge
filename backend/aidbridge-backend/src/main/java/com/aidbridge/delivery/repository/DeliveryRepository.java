package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.Delivery;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    interface DeliveredTotal {
        UUID getItemId();
        long getQuantity();
    }

    /**
     * Stock already consumed per campaign item: every DELIVERED row, confirmed or not (pending handovers
     * already left the warehouse). FAILED rows carry no items. Meaningful only while the campaign item
     * rows are locked. B10 must exclude voided deliveries here once voids exist (deliveries are immutable,
     * so a void will be a separate record).
     */
    @Query(value = """
        SELECT di.campaign_item_id AS "itemId",
               SUM(di.delivered_quantity) AS "quantity"
        FROM delivery_items di
        JOIN deliveries d ON d.id = di.delivery_id
        WHERE d.campaign_id = :campaignId
          AND di.campaign_item_id IN (:itemIds)
          AND d.outcome = 'DELIVERED'
        GROUP BY di.campaign_item_id
        """, nativeQuery = true)
    List<DeliveredTotal> deliveredTotals(
            @Param("campaignId") UUID campaignId,
            @Param("itemIds") Collection<UUID> itemIds);
}