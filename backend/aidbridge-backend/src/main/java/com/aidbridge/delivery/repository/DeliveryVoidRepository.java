package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.DeliveryVoid;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryVoidRepository
        extends JpaRepository<DeliveryVoid, UUID> {

    @Query("""
            select count(deliveryVoid)
            from DeliveryVoid deliveryVoid
            where deliveryVoid.deliveryId = :deliveryId
              and exists (
                  select aidCase.id
                  from AidCase aidCase
                  where aidCase.id = deliveryVoid.caseId
                    and aidCase.organization.id = :organizationId
              )
            """)
    long countScoped(
            @Param("organizationId") UUID organizationId,
            @Param("deliveryId") UUID deliveryId
    );

    @Query("""
        select deliveryVoid.deliveryId
        from DeliveryVoid deliveryVoid
        where deliveryVoid.caseId = :caseId
          and exists (
              select aidCase.id
              from AidCase aidCase
              where aidCase.id = deliveryVoid.caseId
                and aidCase.organization.id = :organizationId
          )
        order by deliveryVoid.deliveryId
        """)
    List<UUID> findVoidedDeliveryIdsForCaseScoped(
            @Param("caseId") UUID caseId,
            @Param("organizationId") UUID organizationId
    );
}