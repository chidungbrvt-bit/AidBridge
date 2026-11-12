package com.aidbridge.blockchain.repository;

import com.aidbridge.blockchain.entity.BlockchainJob;
import com.aidbridge.blockchain.worker.CaseVerifiedSource;
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

    /**
     * C05: locks the next due CASE_VERIFIED job for the configured chain (SKIP LOCKED, so concurrent workers
     * never take the same job). PROCESSING jobs are retaken only after their lease expired (crash recovery).
     */
    @Query(value = """
            SELECT j.* FROM blockchain_jobs j
            JOIN audit_events a ON a.id = j.audit_event_id
            WHERE a.event_type = 'CASE_VERIFIED'
              AND j.chain_id = :chainId
              AND (j.next_retry_at IS NULL OR j.next_retry_at <= :now)
              AND (j.status = 'PENDING'
                   OR (j.status = 'PROCESSING' AND (j.lease_until IS NULL OR j.lease_until < :now)))
            ORDER BY j.created_at, j.id
            LIMIT 1
            FOR UPDATE OF j SKIP LOCKED
            """, nativeQuery = true)
    Optional<BlockchainJob> lockNextCaseVerifiedJob(@Param("chainId") long chainId, @Param("now") Instant now);

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

    /** C05 part 5: due SUBMITTED CASE_VERIFIED jobs whose receipt should be checked (SKIP LOCKED, leased). */
    @Query(value = """
            SELECT j.* FROM blockchain_jobs j
            JOIN audit_events a ON a.id = j.audit_event_id
            WHERE a.event_type = 'CASE_VERIFIED'
              AND j.chain_id = :chainId
              AND j.status = 'SUBMITTED'
              AND (j.next_retry_at IS NULL OR j.next_retry_at <= :now)
              AND (j.lease_until IS NULL OR j.lease_until < :now)
            ORDER BY j.updated_at, j.id
            LIMIT :batch
            FOR UPDATE OF j SKIP LOCKED
            """, nativeQuery = true)
    List<BlockchainJob> lockSubmittedCaseVerifiedJobs(@Param("chainId") long chainId, @Param("now") Instant now,
            @Param("batch") int batch);
}
