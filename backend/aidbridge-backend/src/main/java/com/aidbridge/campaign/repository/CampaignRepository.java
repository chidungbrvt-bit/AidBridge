package com.aidbridge.campaign.repository;

import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.organization.entity.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    @org.springframework.data.jpa.repository.Query("select c from Campaign c join fetch c.aidCase a join fetch c.organization o where a.publicRef = :ref")
    Optional<Campaign> findByPublicCaseRef(@org.springframework.data.repository.query.Param("ref") byte[] ref);

    boolean existsByAidCase_Id(UUID caseId);

    @Query("""
            SELECT c.id FROM Campaign c
            WHERE c.aidCase.id = :caseId AND c.organization.id = :orgId AND c.status = :status
            """)
    Optional<UUID> findScopedCampaignId(
            @Param("caseId") UUID caseId, @Param("orgId") UUID orgId, @Param("status") Campaign.Status status);

    @EntityGraph(attributePaths = {"organization", "aidCase"})
    Optional<Campaign> findWithDetailsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM Campaign c
            JOIN FETCH c.organization
            JOIN FETCH c.aidCase
            WHERE c.id = :id AND c.organization.id IN :orgs
            """)
    Optional<Campaign> lockScoped(@Param("id") UUID id, @Param("orgs") Collection<UUID> organizationIds);

    @EntityGraph(attributePaths = {"organization", "aidCase"})
    @Query("""
            SELECT c FROM Campaign c
            WHERE c.organization.status = :orgStatus
              AND c.status IN :statuses
            """)
    Page<Campaign> findPublicPage(
            @Param("orgStatus") Organization.Status orgStatus,
            @Param("statuses") Collection<Campaign.Status> statuses,
            Pageable pageable
    );
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM disputes d
                WHERE d.aid_case_id = :caseId
                  AND d.status IN ('OPEN', 'UNDER_REVIEW')
            )
            """, nativeQuery = true)
    boolean hasActiveDispute(@Param("caseId") UUID caseId);

    interface PublicCampaignRef {
        UUID getCampaignId();
        UUID getCaseId();
        UUID getOrganizationId();
    }

    /** Scalars only, so callers can lock rows before any Campaign entity enters the persistence context. */
    @Query("""
            SELECT c.id AS campaignId, c.aidCase.id AS caseId, c.organization.id AS organizationId
            FROM Campaign c
            WHERE c.id = :id AND c.status <> :hidden AND c.organization.status = :orgStatus
            """)
    Optional<PublicCampaignRef> findPublicRef(
            @Param("id") UUID id,
            @Param("hidden") Campaign.Status hidden,
            @Param("orgStatus") Organization.Status orgStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c FROM Campaign c
        JOIN FETCH c.organization
        WHERE c.aidCase.id = :caseId
          AND c.organization.id = :orgId
        """)
    Optional<Campaign> lockByCaseAndOrganization(
            @Param("caseId") UUID caseId,
            @Param("orgId") UUID orgId
    );
}
