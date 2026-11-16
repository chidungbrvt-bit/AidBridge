package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.DeliveryConfirmation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryConfirmationRepository extends JpaRepository<DeliveryConfirmation, UUID> {

    @Query("""
            select confirmation
            from DeliveryConfirmation confirmation
            where confirmation.deliveryId = :deliveryId
              and exists (
                  select delivery.id
                  from Delivery delivery, AidCase aidCase
                  where delivery.id = confirmation.deliveryId
                    and aidCase.id = delivery.caseId
                    and aidCase.organization.id = :organizationId
              )
            order by confirmation.reviewedAt desc, confirmation.id desc
            """)
    List<DeliveryConfirmation> findHistoryInOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("deliveryId") UUID deliveryId
    );

    @Query("""
            select count(confirmation)
            from DeliveryConfirmation confirmation
            where confirmation.deliveryId = :deliveryId
              and confirmation.decision = 'CONFIRMED'
              and exists (
                  select delivery.id
                  from Delivery delivery, AidCase aidCase
                  where delivery.id = confirmation.deliveryId
                    and aidCase.id = delivery.caseId
                    and aidCase.organization.id = :organizationId
              )
            """)
    long countConfirmedInOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("deliveryId") UUID deliveryId
    );

    interface ConfirmedTotal {
        UUID getItemId();
        long getQuantity();
    }

    @Query("""
        select item.campaignItemId as itemId,
               sum(item.quantity) as quantity
        from DeliveryItem item, Delivery delivery, AidCase aidCase
        where item.deliveryId = delivery.id
          and aidCase.id = delivery.caseId
          and aidCase.organization.id = :organizationId
          and delivery.campaignId = :campaignId
          and delivery.outcome = :outcome
          and exists (
              select confirmation.id
              from DeliveryConfirmation confirmation
              where confirmation.deliveryId = delivery.id
                and confirmation.decision = 'CONFIRMED'
          )
          and not exists (
              select deliveryVoid.id
              from DeliveryVoid deliveryVoid
              where deliveryVoid.deliveryId = delivery.id
          )
        group by item.campaignItemId
        """)
    List<ConfirmedTotal> confirmedTotalsScoped(
            @Param("organizationId") UUID organizationId,
            @Param("campaignId") UUID campaignId,
            @Param("outcome")
            com.aidbridge.delivery.entity.Delivery.Outcome outcome
    );

    @Query("""
        select confirmation
        from DeliveryConfirmation confirmation
        where exists (
            select delivery.id
            from Delivery delivery, AidCase aidCase
            where delivery.id = confirmation.deliveryId
              and delivery.caseId = :caseId
              and aidCase.id = delivery.caseId
              and aidCase.organization.id = :organizationId
        )
        order by confirmation.reviewedAt desc, confirmation.id desc
        """)
    List<DeliveryConfirmation> findAllForCaseScoped(
            @Param("caseId") UUID caseId,
            @Param("organizationId") UUID organizationId
    );
}
