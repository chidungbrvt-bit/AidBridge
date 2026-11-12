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

    // V5 retry bookkeeping, mapped for the C05 worker. last_error holds a stable reason code only.
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

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

    // ---- C05 worker state transitions -------------------------------------------------------------
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String FAILED = "FAILED";

    public boolean isPrepared() {
        return evidenceCommitment != null;
    }

    /**
     * Stores the private manifest, encrypted salt and resulting commitment exactly once (D48). Retries must
     * reuse them; preparing twice would produce a different commitment for the same eventId.
     */
    public void attachPreparation(String manifestJson, byte[] saltCiphertextValue, byte[] commitment) {
        if (isPrepared()) {
            throw new IllegalStateException("JOB_ALREADY_PREPARED");
        }
        if (manifestJson == null || saltCiphertextValue == null || commitment == null || commitment.length != 32) {
            throw new IllegalArgumentException("Preparation requires manifest, salt ciphertext and a 32-byte commitment");
        }
        this.manifestPrivate = manifestJson;
        this.saltCiphertext = saltCiphertextValue.clone();
        this.evidenceCommitment = commitment.clone();
        touch();
    }

    /** Short lease so a crashed worker's job becomes claimable again after {@code leaseUntilValue}. */
    public void claim(Instant leaseUntilValue) {
        if (!PENDING.equals(status) && !PROCESSING.equals(status)) {
            throw new IllegalStateException("JOB_NOT_CLAIMABLE: " + status);
        }
        this.status = PROCESSING;
        this.leaseUntil = leaseUntilValue;
        touch();
    }

    /**
     * The submit-phase lease ends here: receipt polling takes its own lease (leaseForPolling), so a
     * leftover claim lease must not hide the job from the poller.
     */
    public void markSubmitted() {
        requireStatus(PROCESSING);
        this.status = SUBMITTED;
        this.leaseUntil = null;
        this.nextRetryAt = null;
        touch();
    }

    /** Only after the receipt and CaseVerified log matched every expected field (BR-10). */
    public void markConfirmed() {
        requireStatus(SUBMITTED);
        this.status = CONFIRMED;
        this.leaseUntil = null;
        this.nextRetryAt = null;
        this.lastError = null;
        touch();
    }

    /** Keeps the job (and its eventId/salt/commitment) for a later retry; never creates a new event. */
    public void scheduleRetry(String reasonCode, Instant retryAt) {
        this.retryCount++;
        this.lastError = reasonCode;
        this.nextRetryAt = retryAt;
        this.leaseUntil = null;
        touch();
    }

    /** Terminal integrity failure (e.g. EventIdConflict, revert); needs a human, never auto-resigned. */
    public void markFailed(String reasonCode) {
        this.status = FAILED;
        this.lastError = reasonCode;
        this.leaseUntil = null;
        touch();
    }

    /** Lease while a SUBMITTED job's receipt is being checked, so concurrent workers skip it. */
    public void leaseForPolling(Instant leaseUntilValue) {
        requireStatus(SUBMITTED);
        this.leaseUntil = leaseUntilValue;
        touch();
    }

    /** Receipt not available yet (or block not canonical yet): check again later without counting a retry. */
    public void deferPolling(Instant nextCheckAt) {
        requireStatus(SUBMITTED);
        this.nextRetryAt = nextCheckAt;
        this.leaseUntil = null;
        touch();
    }

    private void requireStatus(String expected) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("INVALID_JOB_TRANSITION: " + status + " -> expected " + expected);
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
