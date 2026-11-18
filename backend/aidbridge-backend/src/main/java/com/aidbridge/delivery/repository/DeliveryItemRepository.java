package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.DeliveryItem;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryItemRepository extends JpaRepository<DeliveryItem, UUID> {
    interface ReviewItem {
        UUID getCampaignItemId();
        String getItemName();
        String getUnit();
        int getQuantity();
    }

    @Query("""
        select item.campaignItemId as campaignItemId, campaignItem.itemName as itemName,
               campaignItem.unit as unit, item.quantity as quantity
        from DeliveryItem item, CampaignItem campaignItem
        where item.deliveryId = :deliveryId
          and campaignItem.id = item.campaignItemId
          and campaignItem.campaign.id = item.campaignId
          and campaignItem.organizationId = :organizationId
        order by item.campaignItemId
        """)
    List<ReviewItem> findReviewItems(
            @Param("deliveryId") UUID deliveryId, @Param("organizationId") UUID organizationId);

    @Query("""
        select item
        from DeliveryItem item
        where exists (
            select delivery.id
            from Delivery delivery, AidCase aidCase
            where delivery.id = item.deliveryId
              and delivery.caseId = :caseId
              and aidCase.id = delivery.caseId
              and aidCase.organization.id = :organizationId
        )
        order by item.deliveryId, item.campaignItemId
        """)
    List<DeliveryItem> findAllForCaseScoped(
            @Param("caseId") UUID caseId,
            @Param("organizationId") UUID organizationId
    );
}