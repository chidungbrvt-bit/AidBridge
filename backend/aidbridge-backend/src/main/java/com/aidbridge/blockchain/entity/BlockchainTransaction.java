package com.aidbridge.blockchain.entity;

import com.aidbridge.blockchain.adapter.SignedTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One signed attempt for a blockchain job (V5 + V9 columns). Created in state PREPARED, with the exact
 * signed bytes (encrypted), hash, signer and nonce, BEFORE broadcast; a retry reuses these bytes instead
 * of signing again. PREPARED -> SUBMITTED -> CONFIRMED | REVERTED.
 */
@Entity
@Table(name = "blockchain_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockchainTransaction {

    public static final String PREPARED = "PREPARED";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REVERTED = "REVERTED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blockchain_job_id", nullable = false, updatable = false)
    private BlockchainJob job;

    @Column(name = "tx_hash", length = 66, nullable = false, unique = true, updatable = false)
    private String txHash;

    @Column(name = "status", length = 20, nullable = false)
    private String status = PREPARED;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "receipt_status")
    private Short receiptStatus;

    /** NOT NULL in V5; holds the preparation time until the bytes are actually broadcast. */
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "chain_id", nullable = false, updatable = false)
    private long chainId;

    @Column(name = "contract_address", length = 42, updatable = false)
    private String contractAddress;

    @Column(name = "signer_address", length = 42, updatable = false)
    private String signerAddress;

    @Column(name = "nonce", updatable = false)
    private Long nonce;

    @Column(name = "signed_tx_ciphertext", columnDefinition = "bytea", updatable = false)
    private byte[] signedTxCiphertext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "receipt_private", columnDefinition = "jsonb")
    private String receiptPrivate;

    @Column(name = "prepared_at", nullable = false, updatable = false)
    private Instant preparedAt;

    public static BlockchainTransaction prepared(BlockchainJob job, SignedTransaction signed, byte[] signedTxCiphertext,
            Instant now) {
        BlockchainTransaction attempt = new BlockchainTransaction();
        attempt.job = Objects.requireNonNull(job);
        attempt.txHash = signed.transactionHash();
        attempt.chainId = signed.chainId();
        attempt.contractAddress = signed.to();
        attempt.signerAddress = signed.from();
        attempt.nonce = signed.nonce().longValueExact();
        attempt.signedTxCiphertext = Objects.requireNonNull(signedTxCiphertext).clone();
        attempt.preparedAt = now;
        attempt.submittedAt = now;
        return attempt;
    }

    public void markSubmitted(Instant at) {
        requireStatus(PREPARED);
        status = SUBMITTED;
        submittedAt = at;
        errorMessage = null;
    }

    public void markConfirmed(long block, String receiptJson, Instant at) {
        requireStatus(SUBMITTED);
        status = CONFIRMED;
        receiptStatus = 1;
        blockNumber = block;
        receiptPrivate = receiptJson;
        confirmedAt = at;
        errorMessage = null;
    }

    public void markReverted(long block, String receiptJson, String reasonCode) {
        requireStatus(SUBMITTED);
        status = REVERTED;
        receiptStatus = 0;
        blockNumber = block;
        receiptPrivate = receiptJson;
        errorMessage = reasonCode;
    }

    /** Stable reason code only (e.g. RPC_UNAVAILABLE); never raw RPC payloads or secrets. */
    public void recordError(String reasonCode) {
        errorMessage = reasonCode;
    }

    private void requireStatus(String expected) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("INVALID_TRANSACTION_TRANSITION: " + status + " -> expected " + expected);
        }
    }
}
