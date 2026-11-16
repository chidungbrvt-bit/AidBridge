package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.DeliveryEvidenceLink;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryEvidenceLinkRepository extends JpaRepository<DeliveryEvidenceLink, UUID> {
    @Query("""
            select distinct link.evidenceVersionId
            from DeliveryEvidenceLink link
            where link.deliveryId = :deliveryId
              and link.caseId = :caseId
              and link.organizationId = :organizationId
            order by link.evidenceVersionId
            """)
    List<UUID> findSnapshotVersionIdsScoped(
            @Param("deliveryId") UUID deliveryId,
            @Param("caseId") UUID caseId,
            @Param("organizationId") UUID organizationId
    );

    @Query("""
        select link
        from DeliveryEvidenceLink link
        where link.caseId = :caseId
          and link.organizationId = :organizationId
        order by link.deliveryId, link.evidenceVersionId, link.id
        """)
    List<DeliveryEvidenceLink> findAllForCaseScoped(
            @Param("caseId") UUID caseId,
            @Param("organizationId") UUID organizationId
    );
}