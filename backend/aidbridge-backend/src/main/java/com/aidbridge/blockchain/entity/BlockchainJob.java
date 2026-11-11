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

    public BlockchainJob(AuditEvent auditEvent) {
        this.auditEvent = auditEvent;
        this.logicalEventId = ChainRefs.randomBytes32();
        this.chainSequence = 1L;
        this.schemaVersion = 1;
    }
}