package com.aidbridge.blockchain.entity;

import com.aidbridge.audit.entity.AuditEvent;
import com.aidbridge.shared.util.ChainRefs;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "blockchain_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockchainJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_event_id", nullable = false, unique = true, updatable = false)
    private AuditEvent auditEvent;

    @Column(name = "chain_id", nullable = false, updatable = false)
    private long chainId = 43113L;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "logical_event_id", nullable = false, unique = true, updatable = false, columnDefinition = "bytea")
    private byte[] logicalEventId;

    @Column(name = "chain_sequence", nullable = false, updatable = false)
    private long chainSequence = 1L;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private short schemaVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest_private", columnDefinition = "jsonb")
    private String manifestPrivate;

    @Column(name = "salt_ciphertext", columnDefinition = "bytea")
    private byte[] saltCiphertext;

    @Column(name = "evidence_commitment", columnDefinition = "bytea")
    private byte[] evidenceCommitment;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    // V12: case milestones are ordered per case; null only for future organization-level jobs.
    @Column(name = "aid_case_id", updatable = false)
    private UUID caseId;

    /** First milestone of a case (CASE_VERIFIED). Behaviour unchanged for B05. */
    public BlockchainJob(AuditEvent auditEvent) {
        this(auditEvent, 1L);
    }

    /**
     * Later milestones. The case comes from the event itself so it cannot disagree with it;
     * the caller assigns the sequence while holding the case row lock.
     */
    public BlockchainJob(AuditEvent auditEvent, long chainSequence) {
        if (chainSequence < 1) {
            throw new IllegalArgumentException("chainSequence must be at least 1");
        }
        this.auditEvent = Objects.requireNonNull(auditEvent);
        this.caseId = auditEvent.getCaseId();
        this.logicalEventId = ChainRefs.randomBytes32();
        this.chainSequence = chainSequence;
        this.schemaVersion = 1;
    }
}