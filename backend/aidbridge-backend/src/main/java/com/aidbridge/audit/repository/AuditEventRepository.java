package com.aidbridge.audit.repository;

import com.aidbridge.audit.entity.AuditEvent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * One row per audit event for the T01/T02 audit trails. Read-only: it never touches payload_private,
     * manifest_private, salt_ciphertext, signed_tx_ciphertext or receipt_private.
     */
    interface TrailRow {
        UUID getEventId();
        String getEventType();
        String getEntityType();
        String getOccurredAtUtc();
        UUID getActorId();
        String getActorName();
        String getJobStatus();
        String getJobLastError();
        Long getJobChainId();
        Boolean getReceiptVerified();
        Long getChainSequence();
        String getLogicalEventId();
        String getTxHash();
        String getTxStatus();
        Long getTxBlockNumber();
        Long getTxChainId();
        String getTxContractAddress();
    }

    /*
     * Attempt selection (deliberate): PREPARED attempts were never broadcast, so their hash is not shown.
     * Among the others prefer CONFIRMED, then REVERTED, then SUBMITTED; ties go to the latest submitted_at,
     * then id. V13 allows at most one open attempt per job, so in practice this picks the only attempt.
     */
    String CHAIN_COLUMNS = """
            COALESCE(t.receipt_status = 1 AND t.chain_id = j.chain_id
                AND t.receipt_private->>'transactionHash' = t.tx_hash
                AND t.receipt_private->>'contractAddress' = lower(t.contract_address)
                AND t.receipt_private->>'eventId' = '0x' || encode(j.logical_event_id,'hex')
                AND t.receipt_private->>'chainSequence' = j.chain_sequence::text
                AND t.receipt_private->>'eventType' = a.event_type
                AND t.receipt_private->>'schemaVersion' = j.schema_version::text
                AND t.receipt_private->>'orgRef' = (SELECT '0x'||encode(o.public_ref,'hex') FROM organizations o WHERE o.id=a.organization_id)
                AND t.receipt_private->>'caseRef' = (SELECT '0x'||encode(c.public_ref,'hex') FROM aid_cases c WHERE c.id=a.aid_case_id)
                AND t.receipt_private->>'status' = '1'
                AND t.receipt_private->>'blockNumber' = t.block_number::text
                AND (t.receipt_private->>'blockHash') ~ '^0x[0-9a-f]{64}$'
                AND (t.receipt_private->>'logIndex') ~ '^(0|[1-9][0-9]*)$', false) AS "receiptVerified",
            j.chain_sequence AS "chainSequence",
            j.status AS "jobStatus",
            j.last_error AS "jobLastError",
            j.chain_id AS "jobChainId",
            CASE WHEN j.id IS NULL THEN NULL ELSE '0x' || encode(j.logical_event_id, 'hex') END AS "logicalEventId",
            t.tx_hash AS "txHash",
            t.status AS "txStatus",
            t.block_number AS "txBlockNumber",
            t.chain_id AS "txChainId",
            t.contract_address AS "txContractAddress"
            """;

    String CHAIN_JOINS = """
            LEFT JOIN blockchain_jobs j ON j.audit_event_id = a.id
            LEFT JOIN LATERAL (
                SELECT x.tx_hash, x.status, x.block_number, x.chain_id, x.contract_address, x.receipt_status, x.receipt_private
                FROM blockchain_transactions x
                WHERE x.blockchain_job_id = j.id AND x.status <> 'PREPARED'
                ORDER BY CASE x.status WHEN 'CONFIRMED' THEN 0 WHEN 'REVERTED' THEN 1 ELSE 2 END,
                         x.submitted_at DESC, x.id DESC
                LIMIT 1
            ) t ON true
            """;

    String OCCURRED_AT = """
            to_char(a.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS "occurredAtUtc"
            """;

    /** T01: every event of one case, scoped to its organization. Stable order: time, then id. */
    @Query(value = """
            SELECT a.id AS "eventId", a.event_type AS "eventType", a.entity_type AS "entityType",
            """ + OCCURRED_AT + """
            , a.actor_user_id AS "actorId", u.full_name AS "actorName",
            """ + CHAIN_COLUMNS + """
            FROM audit_events a
            LEFT JOIN users u ON u.id = a.actor_user_id
            """ + CHAIN_JOINS + """
            WHERE a.aid_case_id = :caseId AND a.organization_id = :orgId
            ORDER BY (j.chain_sequence IS NOT NULL) ASC, j.chain_sequence ASC NULLS FIRST, a.occurred_at ASC, a.id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<TrailRow> findInternalTrail(@Param("caseId") UUID caseId, @Param("orgId") UUID orgId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = """
            SELECT count(*) FROM audit_events a
            WHERE a.aid_case_id = :caseId AND a.organization_id = :orgId
            """, nativeQuery = true)
    long countInternalTrail(@Param("caseId") UUID caseId, @Param("orgId") UUID orgId);

    /**
     * T02: allow-listed event types only. Actor and entity columns are NULL at SQL level, so no identity can
     * reach the public mapper even by mistake.
     */
    @Query(value = """
            SELECT a.id AS "eventId", a.event_type AS "eventType", CAST(NULL AS varchar) AS "entityType",
            """ + OCCURRED_AT + """
            , CAST(NULL AS uuid) AS "actorId", CAST(NULL AS varchar) AS "actorName",
            """ + CHAIN_COLUMNS + """
            FROM audit_events a
            """ + CHAIN_JOINS + """
            WHERE a.aid_case_id = :caseId AND a.organization_id = :orgId AND a.event_type IN (:types)
            ORDER BY (j.chain_sequence IS NOT NULL) ASC, j.chain_sequence ASC NULLS FIRST, a.occurred_at ASC, a.id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<TrailRow> findPublicTrail(@Param("caseId") UUID caseId, @Param("orgId") UUID orgId,
            @Param("types") Collection<String> types, @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = """
            SELECT count(*) FROM audit_events a
            WHERE a.aid_case_id = :caseId AND a.organization_id = :orgId AND a.event_type IN (:types)
            """, nativeQuery = true)
    long countPublicTrail(@Param("caseId") UUID caseId, @Param("orgId") UUID orgId,
            @Param("types") Collection<String> types);
}
