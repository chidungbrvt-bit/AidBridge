package com.aidbridge.blockchain.repository;

import com.aidbridge.blockchain.entity.BlockchainJob;
import com.aidbridge.blockchain.worker.CaseVerifiedSource;
import com.aidbridge.blockchain.worker.CommitmentMilestoneSource;
import com.aidbridge.blockchain.worker.DeliveryMilestoneSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockchainJobRepository extends JpaRepository<BlockchainJob, UUID> {

    /**
     * Highest chain sequence already assigned to a case (0 if none). MAX+1 is race-free only while the
     * caller holds the case row lock; UNIQUE (aid_case_id, chain_sequence) backs that up in the database.
     */
    @Query("SELECT COALESCE(MAX(j.chainSequence), 0) FROM BlockchainJob j WHERE j.caseId = :caseId")
    long maxChainSequence(@Param("caseId") UUID caseId);

    /** Returns the blocking reason or READY for a case milestone whose sequence is greater than one. */
    @Query(value = """
            SELECT CASE
                WHEN p.id IS NULL THEN 'PREDECESSOR_MISSING'
                WHEN p.status = 'FAILED' THEN 'PREDECESSOR_FAILED'
                WHEN p.status <> 'CONFIRMED' THEN 'PREDECESSOR_NOT_CONFIRMED'
                WHEN p.chain_id <> :chainId OR NOT EXISTS (
                    SELECT 1
                    FROM blockchain_transactions t
                    WHERE t.blockchain_job_id = p.id
                      AND t.chain_id = :chainId
                      AND lower(t.contract_address) = lower(:registryAddress)
                      AND t.status = 'CONFIRMED'
                      AND t.receipt_status = 1
                ) THEN 'PREDECESSOR_DEPLOYMENT_MISMATCH'
                WHEN EXISTS (
                    SELECT 1
                    FROM blockchain_jobs earlier
                    WHERE earlier.aid_case_id = :caseId
                      AND earlier.chain_sequence < p.chain_sequence
                      AND earlier.status <> 'CONFIRMED'
                ) THEN 'EARLIER_MILESTONE_NOT_CONFIRMED'
                ELSE 'READY'
            END
            FROM (SELECT 1) AS candidate
            LEFT JOIN blockchain_jobs p
              ON p.aid_case_id = :caseId
             AND p.chain_sequence = :chainSequence - 1
            """, nativeQuery = true)
    String findPredecessorState(@Param("caseId") UUID caseId,
            @Param("chainSequence") long chainSequence, @Param("chainId") long chainId,
            @Param("registryAddress") String registryAddress);

    /**
     * Locks the next due supported job. Goods milestones are enabled on local C06 chain 31337.
     * PROCESSING jobs are retaken only after their lease expired (crash recovery).
     */
    @Query(value = """
            SELECT j.* FROM blockchain_jobs j
            JOIN audit_events a ON a.id = j.audit_event_id
            WHERE (a.event_type = 'CASE_VERIFIED'
                OR (:chainId = 31337 AND a.event_type IN (
                    'AID_PLEDGED', 'COMMITMENT_REDUCED', 'GOODS_RECEIVED', 'DELIVERY_RECORDED')))
              AND j.chain_id = :chainId
              AND NOT EXISTS (
                  SELECT 1 FROM blockchain_transactions t
                  WHERE t.chain_id = :chainId AND LOWER(t.signer_address) = LOWER(:signer)
                    AND t.status IN ('PREPARED', 'SUBMITTED') AND t.blockchain_job_id <> j.id)
              AND (a.event_type <> 'CASE_VERIFIED' OR NOT EXISTS (
                  SELECT 1 FROM blockchain_jobs previous
                  WHERE previous.aid_case_id = j.aid_case_id AND previous.chain_sequence < j.chain_sequence
                    AND previous.status <> 'CONFIRMED'))
              AND (j.next_retry_at IS NULL OR j.next_retry_at <= :now)
              AND (j.status = 'PENDING'
                   OR (j.status = 'PROCESSING' AND (j.lease_until IS NULL OR j.lease_until < :now)))
            ORDER BY j.created_at, j.id
            LIMIT 1
            FOR UPDATE OF j SKIP LOCKED
            """, nativeQuery = true)
    Optional<BlockchainJob> lockNextMilestoneJob(@Param("chainId") long chainId, @Param("signer") String signer,
            @Param("now") Instant now);

    /** C05: private source rows for the manifest and envelope of one CASE_VERIFIED job. */
    @Query(value = """
            SELECT j.id AS "jobId",
                   a.event_key AS "verificationId",
                   to_char(a.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS "occurredAtUtc",
                   CAST(a.payload_private AS text) AS "payloadPrivate",
                   o.id AS "organizationId",
                   o.status AS "organizationStatus",
                   o.public_ref AS "orgPublicRef",
                   o.relayer_address AS "relayerAddress",
                   c.id AS "caseId",
                   c.public_ref AS "casePublicRef",
                   CAST(r.snapshot_private AS text) AS "reviewedSnapshot"
            FROM blockchain_jobs j
            JOIN audit_events a ON a.id = j.audit_event_id
            JOIN organizations o ON o.id = a.organization_id
            JOIN aid_cases c ON c.id = a.aid_case_id AND c.organization_id = o.id
            LEFT JOIN case_revisions r ON r.aid_case_id = c.id AND r.organization_id = o.id
                 AND r.case_version = CAST(a.payload_private ->> 'reviewedCaseVersion' AS bigint)
            WHERE j.id = :jobId AND a.event_type = 'CASE_VERIFIED'
            """, nativeQuery = true)
    Optional<CaseVerifiedSource> findCaseVerifiedSource(@Param("jobId") UUID jobId);

    @Query(value = """
            SELECT x.id AS "contributionId",
                   CAST(a.payload_private AS text) AS "payloadPrivate",
                   CAST((SELECT jsonb_agg(jsonb_build_object('contributionItemId', i.id,
                                   'campaignItemId', i.campaign_item_id, 'quantity', i.pledged_quantity))
                         FROM contribution_items i
                         WHERE i.contribution_id = x.id AND i.campaign_id = x.campaign_id
                           AND i.organization_id = o.id) AS text) AS "pledgedLines",
                   r.id AS "receiptId",
                   to_char(r.received_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "receivedAtUtc",
                   CAST((SELECT jsonb_agg(jsonb_build_object('contributionItemId', i.id,
                                   'campaignItemId', i.campaign_item_id, 'quantity', ri.received_quantity))
                         FROM goods_receipt_items ri
                         JOIN contribution_items i ON i.id = ri.contribution_item_id AND i.contribution_id = x.id
                              AND i.campaign_id = x.campaign_id AND i.organization_id = o.id
                         WHERE ri.goods_receipt_id = r.id AND ri.contribution_id = x.id) AS text) AS "receivedLines",
                   to_char(a.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS "occurredAtUtc",
                   o.status AS "organizationStatus",
                   o.relayer_address AS "relayerAddress",
                   o.public_ref AS "orgPublicRef",
                   c.public_ref AS "casePublicRef",
                   x.public_ref AS "commitmentRef"
            FROM blockchain_jobs j
            JOIN audit_events a ON a.id = j.audit_event_id AND a.aid_case_id = j.aid_case_id
            JOIN organizations o ON o.id = a.organization_id
            JOIN aid_cases c ON c.id = j.aid_case_id AND c.organization_id = o.id
            LEFT JOIN goods_receipts r ON a.event_type = 'GOODS_RECEIVED' AND r.id = a.entity_id
                 AND r.id = a.event_key AND r.received_by = a.actor_user_id
            JOIN contributions x ON x.id = COALESCE(r.contribution_id, a.entity_id) AND x.organization_id = o.id
            JOIN campaigns ca ON ca.id = x.campaign_id AND ca.aid_case_id = c.id AND ca.organization_id = o.id
            WHERE j.id = :jobId AND (
                (a.entity_type = 'CONTRIBUTION' AND x.donor_user_id = a.actor_user_id
                    AND ((a.event_type = 'AID_PLEDGED' AND a.event_key = x.id)
                      OR (a.event_type = 'COMMITMENT_REDUCED' AND a.event_key <> x.id)))
                OR (a.event_type = 'GOODS_RECEIVED' AND a.entity_type = 'GOODS_RECEIPT' AND r.id IS NOT NULL))
            """, nativeQuery = true)
    Optional<CommitmentMilestoneSource> findCommitmentMilestoneSource(@Param("jobId") UUID jobId);

    @Query(value = """
            SELECT d.id AS "deliveryId", d.campaign_id AS "campaignId", d.delivery_ref AS "deliveryRef",
                   CAST(a.payload_private AS text) AS "payloadPrivate",
                   to_char(a.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS "occurredAtUtc",
                   to_char(d.delivered_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "handedOverAtUtc",
                   o.status AS "organizationStatus", o.relayer_address AS "relayerAddress",
                   o.public_ref AS "orgPublicRef", c.public_ref AS "casePublicRef",
                   CAST((SELECT jsonb_agg(jsonb_build_object('campaignItemId', i.campaign_item_id,
                                   'quantity', i.delivered_quantity))
                         FROM delivery_items i WHERE i.delivery_id = d.id AND i.campaign_id = d.campaign_id)
                         AS text) AS "deliveredLines",
                   CAST((SELECT jsonb_agg(jsonb_build_object('id', v.id, 'sha256', v.sha256_hex,
                                   'versionNo', v.version_number))
                         FROM delivery_evidence_links l
                         JOIN evidence_versions v ON v.id = l.evidence_version_id AND v.aid_case_id = c.id
                              AND v.organization_id = o.id
                         WHERE l.delivery_id = d.id AND l.aid_case_id = c.id AND l.organization_id = o.id
                           AND l.purpose = 'HANDOVER_PROOF' AND l.linked_by = d.recorded_by)
                         AS text) AS "evidenceVersions"
            FROM blockchain_jobs j
            JOIN audit_events a ON a.id = j.audit_event_id AND a.aid_case_id = j.aid_case_id
            JOIN organizations o ON o.id = a.organization_id
            JOIN aid_cases c ON c.id = j.aid_case_id AND c.organization_id = o.id
            JOIN deliveries d ON d.id = a.entity_id AND d.id = a.event_key AND d.aid_case_id = c.id
                 AND d.recorded_by = a.actor_user_id AND d.outcome = 'DELIVERED'
            JOIN campaigns ca ON ca.id = d.campaign_id AND ca.aid_case_id = c.id AND ca.organization_id = o.id
            WHERE j.id = :jobId AND a.event_type = 'DELIVERY_RECORDED' AND a.entity_type = 'DELIVERY'
            """, nativeQuery = true)
    Optional<DeliveryMilestoneSource> findDeliveryMilestoneSource(@Param("jobId") UUID jobId);

    /** A reduction or receipt needs a confirmed original pledge independently of the immediate predecessor. */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM blockchain_jobs current_job
                JOIN audit_events current_event ON current_event.id = current_job.audit_event_id
                LEFT JOIN goods_receipts r ON current_event.event_type = 'GOODS_RECEIVED' AND r.id = current_event.entity_id
                JOIN audit_events pledge ON pledge.entity_id = COALESCE(r.contribution_id, current_event.entity_id)
                     AND pledge.aid_case_id = current_job.aid_case_id
                     AND pledge.organization_id = current_event.organization_id
                     AND pledge.event_type = 'AID_PLEDGED' AND pledge.entity_type = 'CONTRIBUTION'
                     AND pledge.event_key = pledge.entity_id
                JOIN blockchain_jobs p ON p.audit_event_id = pledge.id
                     AND p.aid_case_id = current_job.aid_case_id AND p.chain_id = current_job.chain_id
                JOIN blockchain_transactions t ON t.blockchain_job_id = p.id AND t.chain_id = p.chain_id
                WHERE current_job.id = :jobId
                  AND (current_event.event_type = 'COMMITMENT_REDUCED' OR r.id IS NOT NULL)
                  AND p.chain_sequence < current_job.chain_sequence AND p.status = 'CONFIRMED'
                  AND p.manifest_private ->> 'commitmentRef' = :commitmentRef
                  AND t.status = 'CONFIRMED' AND t.receipt_status = 1
                  AND lower(t.contract_address) = lower(:registryAddress)
                  AND t.receipt_private ->> 'eventType' = 'AID_PLEDGED'
                  AND t.receipt_private ->> 'eventId' = '0x' || encode(p.logical_event_id, 'hex')
                  AND t.receipt_private ->> 'contractAddress' = lower(t.contract_address)
            )
            """, nativeQuery = true)
    boolean hasConfirmedOriginalPledge(@Param("jobId") UUID jobId, @Param("commitmentRef") String commitmentRef,
            @Param("registryAddress") String registryAddress);

    /** Locks due SUBMITTED jobs for the same supported operations as the claim query. */
    @Query(value = """
            SELECT j.* FROM blockchain_jobs j
            JOIN audit_events a ON a.id = j.audit_event_id
            WHERE (a.event_type = 'CASE_VERIFIED'
                OR (:chainId = 31337 AND a.event_type IN (
                    'AID_PLEDGED', 'COMMITMENT_REDUCED', 'GOODS_RECEIVED', 'DELIVERY_RECORDED')))
              AND j.chain_id = :chainId
              AND j.status = 'SUBMITTED'
              AND (j.next_retry_at IS NULL OR j.next_retry_at <= :now)
              AND (j.lease_until IS NULL OR j.lease_until < :now)
            ORDER BY j.updated_at, j.id
            LIMIT :batch
            FOR UPDATE OF j SKIP LOCKED
            """, nativeQuery = true)
    List<BlockchainJob> lockSubmittedMilestoneJobs(@Param("chainId") long chainId, @Param("now") Instant now,
            @Param("batch") int batch);
}
