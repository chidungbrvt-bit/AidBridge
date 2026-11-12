package com.aidbridge.contribution.repository;

import com.aidbridge.contribution.entity.Contribution;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {

    interface CancelTarget {
        UUID getContributionId();
        UUID getCampaignId();
        UUID getCaseId();
        UUID getOrganizationId();
        UUID getDonorUserId();
    }

    /** Scalars only, so the first load of the Contribution entity is the locked load (F5). */
    @Query("""
            SELECT c.id AS contributionId, c.campaign.id AS campaignId, c.campaign.aidCase.id AS caseId,
                   c.organizationId AS organizationId, c.donorUserId AS donorUserId
            FROM Contribution c
            WHERE c.id = :id
            """)
    Optional<CancelTarget> findCancelTarget(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Contribution c WHERE c.id = :id")
    Optional<Contribution> lockById(@Param("id") UUID id);

    /** G02 page; callers sort by pledgedAt DESC, id (matches idx_contributions_donor_pledged). */
    Page<Contribution> findByDonorUserId(UUID donorUserId, Pageable pageable);

    interface PledgeChainRef {
        UUID getContributionId();
        UUID getAuditEventId();
        String getJobStatus();
    }

    /** AID_PLEDGED audit event and live job status for many contributions in one query. Non-empty input. */
    @Query(value = """
            SELECT a.event_key AS "contributionId", a.id AS "auditEventId", j.status AS "jobStatus"
            FROM audit_events a
            LEFT JOIN blockchain_jobs j ON j.audit_event_id = a.id
            WHERE a.event_type = 'AID_PLEDGED' AND a.event_key IN (:contributionIds)
            """, nativeQuery = true)
    List<PledgeChainRef> findPledgeChainRefs(@Param("contributionIds") Collection<UUID> contributionIds);
}