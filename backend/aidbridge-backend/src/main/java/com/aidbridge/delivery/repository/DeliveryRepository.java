package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.Delivery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
          AND NOT EXISTS (
              SELECT 1
              FROM delivery_voids dv
              WHERE dv.delivery_id = d.id
          )
        GROUP BY di.campaign_item_id
        """, nativeQuery = true)
    List<DeliveredTotal> deliveredTotals(
            @Param("campaignId") UUID campaignId,
            @Param("itemIds") Collection<UUID> itemIds);

    interface ScopedDeliveryRef {
        UUID getDeliveryId();
        UUID getCaseId();
        UUID getCampaignId();
        UUID getOrganizationId();
    }

    @Query("""
        select delivery.id as deliveryId,
               delivery.caseId as caseId,
               delivery.campaignId as campaignId,
               aidCase.organization.id as organizationId
        from Delivery delivery, AidCase aidCase
        where delivery.id = :deliveryId
          and aidCase.id = delivery.caseId
          and aidCase.organization.id in :organizationIds
        """)
    Optional<ScopedDeliveryRef> findScopedRef(
            @Param("deliveryId") UUID deliveryId,
            @Param("organizationIds") Collection<UUID> organizationIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select delivery
        from Delivery delivery
        where delivery.id = :deliveryId
          and delivery.caseId = :caseId
          and delivery.campaignId = :campaignId
          and exists (
              select aidCase.id
              from AidCase aidCase
              where aidCase.id = delivery.caseId
                and aidCase.organization.id = :organizationId
          )
        """)
    Optional<Delivery> lockScoped(
            @Param("deliveryId") UUID deliveryId,
            @Param("caseId") UUID caseId,
            @Param("campaignId") UUID campaignId,
            @Param("organizationId") UUID organizationId
    );

    @Query("""
        select delivery
        from Delivery delivery
        where delivery.caseId = :caseId
          and exists (
              select aidCase.id
              from AidCase aidCase
              where aidCase.id = delivery.caseId
                and aidCase.organization.id = :organizationId
          )
        order by delivery.recordedAt desc, delivery.id desc
        """)
    List<Delivery> findAllForCaseScoped(
            @Param("caseId") UUID caseId,
            @Param("organizationId") UUID organizationId
    );
}